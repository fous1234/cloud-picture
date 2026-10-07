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
import com.example.cloudpicture.image.dto.response.ImageShareVO;
import com.example.cloudpicture.image.dto.response.ImageVO;
import com.example.cloudpicture.image.dto.response.SharedImageVO;
import com.example.cloudpicture.image.entity.Image;
import com.example.cloudpicture.image.mapper.ImageMapper;
import com.example.cloudpicture.image.mapper.ImageTagMapper;
import com.example.cloudpicture.image.service.CosStorage;
import com.example.cloudpicture.image.service.ImageFileSupport;
import com.example.cloudpicture.image.service.ImageService;
import com.example.cloudpicture.image.service.UploaderFiller;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {

    /** 分享 token 随机字节数：编码后 43 字符，不能由图片 ID 推导 */
    private static final int SHARE_TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String KEY_PREFIX = "picture/";
    private static final int MAX_NAME_LENGTH = 256;

    private final ImageMapper imageMapper;
    private final ImageTagMapper imageTagMapper;
    private final CosStorage cosStorage;
    private final UploaderFiller uploaderFiller;
    private final ImageFileSupport imageFileSupport;

    public ImageServiceImpl(ImageMapper imageMapper, ImageTagMapper imageTagMapper, CosStorage cosStorage,
                            UploaderFiller uploaderFiller, ImageFileSupport imageFileSupport) {
        this.imageMapper = imageMapper;
        this.imageTagMapper = imageTagMapper;
        this.cosStorage = cosStorage;
        this.uploaderFiller = uploaderFiller;
        this.imageFileSupport = imageFileSupport;
    }

    /** 校验与入库在同一事务内，任一步失败都会回滚，避免留下指向不存在对象的记录 */
    @Transactional(rollbackFor = Exception.class)
    public ImageVO upload(MultipartFile file, ImageUploadRequest request) {
        String format = imageFileSupport.validateAndGetFormat(file);
        int[] size = imageFileSupport.readImageSize(file);
        if (size == null && imageFileSupport.dimensionReadable(format)) {
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
        List<ImageVO> records = page.getRecords().stream().map(this::toGalleryVO).toList();
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
        return cosStorage.signedDownloadUrl(image.getCosKey(),
                imageFileSupport.downloadFilename(image.getId(), image.getName(), image.getPicFormat()));
    }

    public ImageShareVO getShare(Long id) {
        Image image = requireOwnedImage(id);
        return new ImageShareVO(StringUtils.hasText(image.getShareToken()), image.getShareToken());
    }

    public ImageShareVO createShare(Long id) {
        Image image = requireOwnedImage(id);
        if (image.getReviewStatus() != Image.REVIEW_PASSED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        String token = generateShareToken();
        // 覆盖旧 token，旧链接立即失效；NULL 字段走 set 显式写入
        imageMapper.update(null, new LambdaUpdateWrapper<Image>()
                .eq(Image::getId, id)
                .set(Image::getShareToken, token));
        return new ImageShareVO(true, token);
    }

    public boolean revokeShare(Long id) {
        requireOwnedImage(id);
        return imageMapper.update(null, new LambdaUpdateWrapper<Image>()
                .eq(Image::getId, id)
                .set(Image::getShareToken, (Object) null)) > 0;
    }

    public SharedImageVO getSharedImage(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分享不存在");
        }
        Image image = imageMapper.selectOne(new LambdaQueryWrapper<Image>()
                .eq(Image::getShareToken, token));
        // 无效、已撤销、已删除或审核状态不再通过，统一表现为不存在
        if (image == null || image.getReviewStatus() != Image.REVIEW_PASSED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分享不存在");
        }
        return SharedImageVO.from(image, cosStorage.signedUrl(image.getCosKey()));
    }

    /** 仅图片所有者可管理自己的分享链接；不存在或非所有者统一 404，不泄露资源状态 */
    private Image requireOwnedImage(Long id) {
        Long ownerId = CurrentUser.get().getId();
        Image image = imageMapper.selectOne(new LambdaQueryWrapper<Image>()
                .eq(Image::getId, id)
                .eq(Image::getOwnerId, ownerId));
        if (image == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在或无权操作");
        }
        return image;
    }

    /** 32 字节安全随机 → 无填充 Base64 URL 字符串（43 字符） */
    private static String generateShareToken() {
        byte[] bytes = new byte[SHARE_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public List<String> listTagNames(int limit) {
        return imageTagMapper.listTagNames(limit);
    }

    private ImageVO toVO(Image image) {
        return ImageVO.from(image, cosStorage.signedUrl(image.getCosKey()),
                cosStorage.signedThumbnailUrl(image.getCosKey()));
    }

    private ImageVO toGalleryVO(Image image) {
        ImageVO vo = toVO(image);
        vo.setMediumUrl(cosStorage.signedMediumUrl(image.getCosKey()));
        return vo;
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

