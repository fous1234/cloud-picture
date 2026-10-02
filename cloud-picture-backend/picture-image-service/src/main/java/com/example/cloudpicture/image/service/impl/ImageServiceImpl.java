package com.example.cloudpicture.image.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.image.dto.request.ImageQueryRequest;
import com.example.cloudpicture.image.dto.request.ImageUpdateRequest;
import com.example.cloudpicture.image.dto.request.ImageUploadRequest;
import com.example.cloudpicture.image.dto.response.ImageVO;
import com.example.cloudpicture.image.entity.Image;
import com.example.cloudpicture.image.mapper.ImageMapper;
import com.example.cloudpicture.image.mapper.ImageTagMapper;
import com.example.cloudpicture.image.service.CosStorage;
import com.example.cloudpicture.image.service.ImageService;
import com.example.cloudpicture.image.service.UploaderFiller;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

    private static final Set<String> ALLOWED_FORMATS = Set.of("jpg", "jpeg", "png", "webp");
    /** JDK ImageIO 能读尺寸的格式；WebP 无内置解码器，只能靠文件头魔数校验 */
    private static final Set<String> DIMENSION_READABLE_FORMATS = Set.of("jpg", "jpeg", "png");
    private static final int HEADER_LENGTH = 12;
    private static final String KEY_PREFIX = "picture/";
    private static final int MAX_NAME_LENGTH = 256;
    /** 路径分隔符、控制字符及 Windows 文件名非法字符 */
    private static final Pattern ILLEGAL_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private final ImageMapper imageMapper;
    private final ImageTagMapper imageTagMapper;
    private final CosStorage cosStorage;
    private final UploaderFiller uploaderFiller;
    private final long maxFileSizeBytes;
    private final String maxFileSizeText;

    public ImageServiceImpl(ImageMapper imageMapper, ImageTagMapper imageTagMapper, CosStorage cosStorage,
                             UploaderFiller uploaderFiller,
                             @Value("${picture.upload.max-file-size:10MB}") String maxFileSize) {
        this.imageMapper = imageMapper;
        this.imageTagMapper = imageTagMapper;
        this.cosStorage = cosStorage;
        this.uploaderFiller = uploaderFiller;
        this.maxFileSizeText = maxFileSize;
        this.maxFileSizeBytes = DataSize.parse(maxFileSize).toBytes();
    }

    /** 校验与入库在同一事务内，任一步失败都会回滚，避免留下指向不存在对象的记录 */
    @Transactional(rollbackFor = Exception.class)
    public ImageVO upload(MultipartFile file, ImageUploadRequest request) {
        String format = validateAndGetFormat(file);
        int[] size = readImageSize(file);
        if (size == null && DIMENSION_READABLE_FORMATS.contains(format)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法识别图片内容，请上传有效图片");
        }
        String cosKey = KEY_PREFIX + UUID.randomUUID().toString().replace("-", "") + "." + format;
        // 内容类型由服务端按扩展名确定，避免把客户端声明的类型原样存到对象上
        cosStorage.upload(file, cosKey, "image/" + ("jpg".equals(format) ? "jpeg" : format));

        Image image = new Image();
        image.setCosKey(cosKey);
        image.setName(StringUtils.hasText(request.getName()) ? request.getName() : defaultName(file));
        image.setIntroduction(request.getIntroduction());
        image.setCategory(request.getCategory());
        image.setTags(Image.joinTags(request.getTags()));
        image.setPicSize(file.getSize());
        image.setPicFormat(format);
        if (size != null) {
            image.setPicWidth(size[0]);
            image.setPicHeight(size[1]);
        }
        CurrentUser currentUser = CurrentUser.get();
        image.setOwnerId(currentUser.getId());
        image.setReviewStatus(currentUser.isAdmin() ? Image.REVIEW_PASSED : Image.REVIEW_PENDING);
        try {
            imageMapper.insert(image);
            imageTagMapper.increaseTags(image.tagList());
        } catch (RuntimeException e) {
            // 同事务内两步一起回滚，再尽力清理刚上传的对象，避免留下孤立文件
            try {
                cosStorage.delete(cosKey);
            } catch (RuntimeException cleanupFailure) {
                log.error("清理已上传的 COS 对象失败, key={}", cosKey, cleanupFailure);
            }
            throw e;
        }
        return toVO(image);
    }

    public PageData<ImageVO> pageImages(ImageQueryRequest request) {
        LambdaQueryWrapper<Image> wrapper = request.toQueryWrapper()
                .eq(Image::getReviewStatus, Image.REVIEW_PASSED);
        Page<Image> page = imageMapper.selectPage(Page.of(request.getCurrent(), request.getSize()), wrapper);
        List<ImageVO> records = page.getRecords().stream().map(this::toVO).toList();
        uploaderFiller.fillOwners(records);
        return PageData.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageData<ImageVO> pageMyImages(ImageQueryRequest request) {
        Long ownerId = CurrentUser.get().getId();
        LambdaQueryWrapper<Image> wrapper = request.toQueryWrapper()
                .eq(Image::getOwnerId, ownerId);
        Page<Image> page = imageMapper.selectPage(Page.of(request.getCurrent(), request.getSize()), wrapper);
        List<ImageVO> records = page.getRecords().stream().map(this::toVO).toList();
        uploaderFiller.fillOwners(records);
        return PageData.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public ImageVO getImageById(Long id) {
        Image image = imageMapper.selectById(id);
        if (image == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        CurrentUser currentUser = CurrentUser.get();
        boolean owner = currentUser != null && currentUser.getId().equals(image.getOwnerId());
        boolean admin = currentUser != null && currentUser.isAdmin();
        if (image.getReviewStatus() != Image.REVIEW_PASSED && !owner && !admin) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        return toVO(image);
    }

    public boolean updateImage(ImageUpdateRequest request) {
        Long ownerId = CurrentUser.get().getId();
        Image existing = imageMapper.selectOne(new LambdaQueryWrapper<Image>()
                .eq(Image::getId, request.getId())
                .eq(Image::getOwnerId, ownerId));
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在或无权操作");
        }
        boolean tagsChanged = request.getTags() != null;
        String joinedTags = tagsChanged ? Image.joinTags(request.getTags()) : null;
        Image update = new Image();
        update.setName(request.getName());
        update.setIntroduction(request.getIntroduction());
        update.setCategory(request.getCategory());
        update.setTags(joinedTags);
        LambdaUpdateWrapper<Image> updateWrapper = new LambdaUpdateWrapper<Image>()
                .eq(Image::getId, request.getId())
                .eq(Image::getOwnerId, ownerId);
        if (tagsChanged) {
            updateWrapper.set(Image::getTags, joinedTags);
        }
        boolean updated = imageMapper.update(update, updateWrapper) > 0;
        if (updated && tagsChanged) {
            List<String> oldTags = existing.tagList();
            List<String> newTags = update.tagList();
            imageTagMapper.increaseTags(difference(newTags, oldTags));
            imageTagMapper.decreaseTags(difference(oldTags, newTags));
        }
        return updated;
    }

    public boolean deleteImage(Long id) {
        Long ownerId = CurrentUser.get().getId();
        Image image = imageMapper.selectOne(new LambdaQueryWrapper<Image>()
                .eq(Image::getId, id)
                .eq(Image::getOwnerId, ownerId));
        if (image == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在或无权操作");
        }
        // COS 删除成功后再软删除数据库记录
        cosStorage.delete(image.getCosKey());
        boolean deleted = imageMapper.deleteById(id) > 0;
        if (deleted) {
            imageTagMapper.decreaseTags(image.tagList());
        }
        return deleted;
    }

    public String download(Long id) {
        Image image = imageMapper.selectById(id);
        if (image == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        CurrentUser currentUser = CurrentUser.get();
        boolean owner = currentUser != null && currentUser.getId().equals(image.getOwnerId());
        boolean admin = currentUser != null && currentUser.isAdmin();
        // 未通过审核的图片对外统一表现为不存在，避免泄露资源状态
        if (image.getReviewStatus() != Image.REVIEW_PASSED && !owner && !admin) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        return cosStorage.signedDownloadUrl(image.getCosKey(), downloadFilename(image));
    }

    /** 下载文件名：清理非法字符，仅保留与 picFormat 匹配的扩展名；名称为空时用 image-{id}.{picFormat} */
    private static String downloadFilename(Image image) {
        String name = image.getName() == null ? "" : ILLEGAL_FILENAME_CHARS.matcher(image.getName()).replaceAll("").trim();
        if (name.isEmpty()) {
            return "image-" + image.getId() + "." + image.getPicFormat();
        }
        int dot = name.lastIndexOf('.');
        String format = image.getPicFormat();
        boolean hasMatchingExtension = dot > 0 && dot < name.length() - 1
                && name.substring(dot + 1).equalsIgnoreCase(format);
        return hasMatchingExtension ? name : name + "." + format;
    }

    public List<String> listTagNames(int limit) {
        return imageTagMapper.listTagNames(limit);
    }

    private ImageVO toVO(Image image) {
        return ImageVO.from(image, cosStorage.signedUrl(image.getCosKey()),
                cosStorage.signedThumbnailUrl(image.getCosKey()));
    }

    private String validateAndGetFormat(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片文件不能为空");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片大小不能超过 " + maxFileSizeText);
        }
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片文件名不合法");
        }
        String format = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_FORMATS.contains(format)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 jpg/jpeg/png/webp 格式");
        }
        if (!matchesMagic(readHeader(file), format)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件内容不是有效图片");
        }
        return format;
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = new byte[HEADER_LENGTH];
            int read = inputStream.readNBytes(header, 0, HEADER_LENGTH);
            return read == HEADER_LENGTH ? header : Arrays.copyOf(header, Math.max(read, 0));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "读取上传文件失败");
        }
    }

    /** 文件头魔数校验，避免改扩展名把非图片文件传上来 */
    private static boolean matchesMagic(byte[] header, String format) {
        return switch (format) {
            case "jpg", "jpeg" -> header.length >= 3
                    && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
            case "png" -> header.length >= 8
                    && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                    && (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A
                    && (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A;
            case "webp" -> header.length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }

    /** 只读取图片头部尺寸，不整图解码 */
    private static int[] readImageSize(MultipartFile file) {
        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(file.getInputStream())) {
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

    private static String defaultName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            return "未命名图片";
        }
        return originalFilename.length() > MAX_NAME_LENGTH
                ? originalFilename.substring(0, MAX_NAME_LENGTH) : originalFilename;
    }

    private static List<String> difference(List<String> source, List<String> other) {
        return source.stream().filter(tag -> !other.contains(tag)).toList();
    }
}

