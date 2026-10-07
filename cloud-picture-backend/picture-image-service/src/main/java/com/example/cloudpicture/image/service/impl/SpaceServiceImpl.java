package com.example.cloudpicture.image.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.image.dto.request.ImageUploadRequest;
import com.example.cloudpicture.image.dto.request.SpaceCreateRequest;
import com.example.cloudpicture.image.dto.request.SpaceImageQueryRequest;
import com.example.cloudpicture.image.dto.request.SpaceRenameRequest;
import com.example.cloudpicture.image.dto.response.SpaceImageVO;
import com.example.cloudpicture.image.dto.response.SpaceVO;
import com.example.cloudpicture.image.entity.Image;
import com.example.cloudpicture.image.entity.PrivateSpace;
import com.example.cloudpicture.image.entity.SpaceImage;
import com.example.cloudpicture.image.mapper.PrivateSpaceMapper;
import com.example.cloudpicture.image.mapper.SpaceImageMapper;
import com.example.cloudpicture.image.service.CosStorage;
import com.example.cloudpicture.image.service.ImageFileSupport;
import com.example.cloudpicture.image.service.SpaceService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 私有空间：空间一人一个（uk_owner_id 兜底），图片免审核且所有读写都按 owner_id 过滤。
 * 空间行物理删除（放行重建），图片行软删除（与 t_image 约定一致）
 */
@Slf4j
@Service
public class SpaceServiceImpl implements SpaceService {

    /** 私有对象 Key 前缀：与共享图库分目录便于运维区分，不提供额外安全语义 */
    private static final String KEY_PREFIX = "private/";
    private static final String DEFAULT_SPACE_NAME = "我的私有空间";
    private static final int MAX_NAME_LENGTH = 256;

    private final PrivateSpaceMapper privateSpaceMapper;
    private final SpaceImageMapper spaceImageMapper;
    private final CosStorage cosStorage;
    private final ImageFileSupport imageFileSupport;

    public SpaceServiceImpl(PrivateSpaceMapper privateSpaceMapper, SpaceImageMapper spaceImageMapper,
                            CosStorage cosStorage, ImageFileSupport imageFileSupport) {
        this.privateSpaceMapper = privateSpaceMapper;
        this.spaceImageMapper = spaceImageMapper;
        this.cosStorage = cosStorage;
        this.imageFileSupport = imageFileSupport;
    }

    public SpaceVO getMine() {
        PrivateSpace space = findMySpace();
        if (space == null) {
            return null;
        }
        long[] stats = summarize(space.getId());
        return SpaceVO.from(space, stats[0], stats[1]);
    }

    public SpaceVO create(SpaceCreateRequest request) {
        Long ownerId = CurrentUser.get().getId();
        if (findMySpace() != null) {
            throw new BusinessException(ErrorCode.SPACE_EXISTS, "已创建过私有空间");
        }
        PrivateSpace space = new PrivateSpace();
        space.setOwnerId(ownerId);
        space.setName(StringUtils.hasText(request.getName()) ? request.getName() : DEFAULT_SPACE_NAME);
        try {
            privateSpaceMapper.insert(space);
        } catch (DuplicateKeyException e) {
            // 并发下另一个请求已建好空间，唯一键兜底
            throw new BusinessException(ErrorCode.SPACE_EXISTS, "已创建过私有空间");
        }
        return SpaceVO.from(space, 0L, 0L);
    }

    public boolean rename(SpaceRenameRequest request) {
        PrivateSpace space = requireMySpace();
        PrivateSpace update = new PrivateSpace();
        update.setId(space.getId());
        update.setName(request.getName());
        return privateSpaceMapper.updateById(update) > 0;
    }

    public boolean deleteMine() {
        PrivateSpace space = requireMySpace();
        cascadeDelete(space);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public SpaceImageVO upload(MultipartFile file, ImageUploadRequest request) {
        PrivateSpace space = requireMySpace();
        String format = imageFileSupport.validateAndGetFormat(file);
        int[] size = imageFileSupport.readImageSize(file);
        if (size == null && imageFileSupport.dimensionReadable(format)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无法识别图片内容，请上传有效图片");
        }
        String cosKey = KEY_PREFIX + UUID.randomUUID().toString().replace("-", "") + "." + format;
        // 内容类型由服务端按扩展名确定，与共享图库上传一致
        cosStorage.upload(file, cosKey, "image/" + ("jpg".equals(format) ? "jpeg" : format));

        SpaceImage image = new SpaceImage();
        image.setSpaceId(space.getId());
        image.setOwnerId(space.getOwnerId());
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
        try {
            spaceImageMapper.insert(image);
        } catch (RuntimeException e) {
            try {
                cosStorage.delete(cosKey);
            } catch (RuntimeException cleanupFailure) {
                log.error("清理已上传的私有 COS 对象失败, key={}", cosKey, cleanupFailure);
            }
            throw e;
        }
        return toVO(image);
    }

    public PageData<SpaceImageVO> pageImages(SpaceImageQueryRequest request) {
        PrivateSpace space = requireMySpace();
        LambdaQueryWrapper<SpaceImage> wrapper = request.toQueryWrapper()
                .eq(SpaceImage::getSpaceId, space.getId())
                .eq(SpaceImage::getOwnerId, space.getOwnerId());
        Page<SpaceImage> page = spaceImageMapper.selectPage(Page.of(request.getCurrent(), request.getSize()), wrapper);
        List<SpaceImageVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageData.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public String download(Long id) {
        SpaceImage image = requireMyImage(id);
        return cosStorage.signedDownloadUrl(image.getCosKey(),
                imageFileSupport.downloadFilename(image.getId(), image.getName(), image.getPicFormat()));
    }

    public boolean deleteImage(Long id) {
        SpaceImage image = requireMyImage(id);
        // COS 删除成功后再软删除数据库记录，与共享图库删除顺序一致
        cosStorage.delete(image.getCosKey());
        return spaceImageMapper.deleteById(id) > 0;
    }

    /** 查当前用户的空间，不存在返回 null */
    private PrivateSpace findMySpace() {
        Long ownerId = CurrentUser.get().getId();
        return privateSpaceMapper.selectOne(new LambdaQueryWrapper<PrivateSpace>()
                .eq(PrivateSpace::getOwnerId, ownerId));
    }

    /** 空间不存在一律 404，不泄露资源状态 */
    private PrivateSpace requireMySpace() {
        PrivateSpace space = findMySpace();
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "私有空间不存在或无权操作");
        }
        return space;
    }

    /** 图片不存在或非本人一律 404；管理员无此入口，故不做角色旁路 */
    private SpaceImage requireMyImage(Long id) {
        Long ownerId = CurrentUser.get().getId();
        SpaceImage image = spaceImageMapper.selectOne(new LambdaQueryWrapper<SpaceImage>()
                .eq(SpaceImage::getId, id)
                .eq(SpaceImage::getOwnerId, ownerId));
        if (image == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在或无权操作");
        }
        return image;
    }

    /** 级联删除：先逐张删 COS 对象（单张失败只记日志），再软删图片行，最后物理删除空间行 */
    private void cascadeDelete(PrivateSpace space) {
        List<SpaceImage> images = spaceImageMapper.selectList(new LambdaQueryWrapper<SpaceImage>()
                .eq(SpaceImage::getSpaceId, space.getId()));
        for (SpaceImage image : images) {
            try {
                cosStorage.delete(image.getCosKey());
            } catch (RuntimeException e) {
                log.warn("删除私有图片 COS 对象失败, key={}", image.getCosKey(), e);
            }
        }
        if (!images.isEmpty()) {
            spaceImageMapper.delete(new LambdaQueryWrapper<SpaceImage>()
                    .eq(SpaceImage::getSpaceId, space.getId()));
        }
        privateSpaceMapper.deleteById(space.getId());
    }

    /** 空间统计：未删图片数与总大小；is_delete 条件由 @TableLogic 自动补上 */
    private long[] summarize(Long spaceId) {
        Map<String, Object> row = spaceImageMapper.selectMaps(new QueryWrapper<SpaceImage>()
                        .select("COUNT(*) AS imageCount", "COALESCE(SUM(pic_size), 0) AS totalSize")
                        .eq("space_id", spaceId))
                .stream().findFirst().orElse(Map.of());
        return new long[]{asLong(row.get("imageCount")), asLong(row.get("totalSize"))};
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private SpaceImageVO toVO(SpaceImage image) {
        return SpaceImageVO.from(image, cosStorage.signedUrl(image.getCosKey()),
                cosStorage.signedThumbnailUrl(image.getCosKey()));
    }

    private static String defaultName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            return "未命名图片";
        }
        return originalFilename.length() > MAX_NAME_LENGTH
                ? originalFilename.substring(0, MAX_NAME_LENGTH) : originalFilename;
    }
}