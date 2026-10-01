package com.example.cloudpicture.image.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.image.client.PexelsClient;
import com.example.cloudpicture.image.dto.request.ImageImportRequest;
import com.example.cloudpicture.image.dto.response.ImportResultVO;
import com.example.cloudpicture.image.entity.Image;
import com.example.cloudpicture.image.mapper.ImageMapper;
import com.example.cloudpicture.image.mapper.ImageTagMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Pexels 图源导入：搜索 → 批次与库内去重 → 逐张下载校验 → 上传 COS → 独立事务入库
 */
@Slf4j
@Service
public class ImageImportService {

    private static final int DEFAULT_COUNT = 5;
    private static final int MAX_DOWNLOAD_BYTES = 10 * 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 256;
    private static final String KEY_PREFIX = "picture/";
    private static final String PEXELS_HOST = "images.pexels.com";
    /** 允许的声明类型与文件魔数推断格式的对应关系 */
    private static final Map<String, String> CONTENT_TYPE_FORMATS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final PexelsClient pexelsClient;
    private final ImageMapper imageMapper;
    private final ImageTagMapper imageTagMapper;
    private final CosStorage cosStorage;
    private final TransactionTemplate transactionTemplate;

    public ImageImportService(PexelsClient pexelsClient, ImageMapper imageMapper, ImageTagMapper imageTagMapper,
                              CosStorage cosStorage, PlatformTransactionManager transactionManager) {
        this.pexelsClient = pexelsClient;
        this.imageMapper = imageMapper;
        this.imageTagMapper = imageTagMapper;
        this.cosStorage = cosStorage;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ImportResultVO importImages(ImageImportRequest request) {
        String keyword = request.getKeyword();
        int count = request.getCount() == null ? DEFAULT_COUNT : request.getCount();
        List<String> tags = request.getTags() == null || request.getTags().isEmpty()
                ? List.of(keyword) : request.getTags();
        // 外部 HTTP 不放在数据库事务内；搜索失败直接抛 PEXELS_ERROR，不返回部分结果
        List<PexelsClient.Photo> photos = pexelsClient.search(keyword, count);

        int skipped = 0;
        Set<String> seenIds = new HashSet<>();
        List<PexelsClient.Photo> candidates = new ArrayList<>();
        for (PexelsClient.Photo photo : photos) {
            if (seenIds.add(String.valueOf(photo.getId()))) {
                candidates.add(photo);
            } else {
                skipped++;
            }
        }

        int imported = 0;
        int failed = 0;
        if (!candidates.isEmpty()) {
            Set<String> existingIds = existingSourceIds(candidates);
            for (PexelsClient.Photo photo : candidates) {
                if (existingIds.contains(String.valueOf(photo.getId()))) {
                    skipped++;
                    continue;
                }
                try {
                    importOne(photo, request.getCategory(), tags);
                    imported++;
                } catch (RuntimeException e) {
                    failed++;
                    log.warn("导入 Pexels 图片失败, sourceId={}, message={}", photo.getId(), e.getMessage());
                }
            }
        }
        return new ImportResultVO(imported, skipped, failed);
    }

    /** 库中已存在且未删除的 PEXELS 图片 ID（逻辑删除的图片不参与去重，允许重新导入） */
    private Set<String> existingSourceIds(List<PexelsClient.Photo> candidates) {
        List<String> sourceIds = candidates.stream().map(photo -> String.valueOf(photo.getId())).toList();
        return imageMapper.selectList(new LambdaQueryWrapper<Image>()
                        .select(Image::getSourceId)
                        .eq(Image::getSource, Image.SOURCE_PEXELS)
                        .in(Image::getSourceId, sourceIds)).stream()
                .map(Image::getSourceId)
                .collect(Collectors.toSet());
    }

    /** 单张处理：下载校验 → 上传 COS → 独立事务入库；入库失败尽力清理 COS 对象 */
    private void importOne(PexelsClient.Photo photo, String category, List<String> tags) {
        URI downloadUri = downloadUri(photo.getLarge2xUrl());
        PexelsClient.DownloadedImage downloaded = pexelsClient.download(downloadUri);
        String format = validateDownloadedImage(downloaded);
        int[] actualSize = readImageSize(downloaded.content());
        String cosKey = KEY_PREFIX + UUID.randomUUID().toString().replace("-", "") + "." + format;
        cosStorage.upload(downloaded.content(), cosKey, "image/" + ("jpg".equals(format) ? "jpeg" : format));
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Image image = new Image();
                image.setCosKey(cosKey);
                image.setName(imageName(photo));
                image.setCategory(category);
                image.setTags(Image.joinTags(tags));
                image.setPicSize((long) downloaded.content().length);
                image.setPicWidth(actualSize != null ? actualSize[0] : photo.getWidth());
                image.setPicHeight(actualSize != null ? actualSize[1] : photo.getHeight());
                image.setPicFormat(format);
                image.setOwnerId(CurrentUser.get().getId());
                image.setReviewStatus(Image.REVIEW_PASSED);
                image.setSource(Image.SOURCE_PEXELS);
                image.setSourceId(String.valueOf(photo.getId()));
                image.setSourcePageUrl(photo.getUrl());
                image.setPhotographer(photo.getPhotographer());
                image.setPhotographerUrl(photo.getPhotographerUrl());
                imageMapper.insert(image);
                imageTagMapper.increaseTags(image.tagList());
            });
        } catch (RuntimeException e) {
            try {
                cosStorage.delete(cosKey);
            } catch (RuntimeException cleanupFailure) {
                log.error("清理已上传的 COS 对象失败, key={}", cosKey, cleanupFailure);
            }
            throw e;
        }
    }

    /** 下载地址只能来自 Pexels API 响应：仅接受 HTTPS + images.pexels.com */
    private static URI downloadUri(String url) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PEXELS_ERROR, "Pexels 图片地址不合法");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !PEXELS_HOST.equalsIgnoreCase(uri.getHost())) {
            throw new BusinessException(ErrorCode.PEXELS_ERROR,
                    "Pexels 图片地址不合法，仅接受 images.pexels.com 的 HTTPS 地址");
        }
        return uri;
    }

    /** 校验响应类型、下载大小与文件魔数，返回入库使用的图片格式 */
    private static String validateDownloadedImage(PexelsClient.DownloadedImage downloaded) {
        byte[] content = downloaded.content();
        if (content.length > MAX_DOWNLOAD_BYTES) {
            throw new BusinessException(ErrorCode.PEXELS_ERROR, "图片超过 10MB，已跳过");
        }
        String declared = downloaded.contentType() == null
                ? "" : downloaded.contentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
        String declaredFormat = CONTENT_TYPE_FORMATS.get(declared);
        if (declaredFormat == null) {
            throw new BusinessException(ErrorCode.PEXELS_ERROR, "图片类型不受支持，仅接受 jpeg/png/webp");
        }
        String format = detectFormat(content);
        if (format == null || !format.equals(declaredFormat)) {
            throw new BusinessException(ErrorCode.PEXELS_ERROR, "文件内容不是有效图片或与声明类型不一致");
        }
        return format;
    }

    /** 文件头魔数判断图片格式，与本地图片上传同一规则 */
    private static String detectFormat(byte[] header) {
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A
                && (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A) {
            return "png";
        }
        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return "webp";
        }
        return null;
    }

    /** 优先读取实际下载内容尺寸；WebP 无 JDK 解码器时回退 API 尺寸 */
    private static int[] readImageSize(byte[] content) {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (imageInputStream == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.warn("读取图片尺寸失败: {}", e.getMessage());
            return null;
        }
    }

    private static String imageName(PexelsClient.Photo photo) {
        String alt = photo.getAlt();
        String name = StringUtils.hasText(alt) ? alt : "Pexels 图片 " + photo.getId();
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }
}
