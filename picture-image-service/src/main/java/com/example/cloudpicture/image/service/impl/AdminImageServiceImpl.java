package com.example.cloudpicture.image.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.image.dto.request.ImageQueryRequest;
import com.example.cloudpicture.image.dto.request.ImageReviewRequest;
import com.example.cloudpicture.image.dto.response.ImageVO;
import com.example.cloudpicture.image.entity.Image;
import com.example.cloudpicture.image.mapper.ImageMapper;
import com.example.cloudpicture.image.mapper.ImageTagMapper;
import com.example.cloudpicture.image.service.AdminImageService;
import com.example.cloudpicture.image.service.CosStorage;
import com.example.cloudpicture.image.service.UploaderFiller;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AdminImageServiceImpl implements AdminImageService {

    private final ImageMapper imageMapper;
    private final ImageTagMapper imageTagMapper;
    private final CosStorage cosStorage;
    private final UploaderFiller uploaderFiller;

    public AdminImageServiceImpl(ImageMapper imageMapper, ImageTagMapper imageTagMapper, CosStorage cosStorage,
                                 UploaderFiller uploaderFiller) {
        this.imageMapper = imageMapper;
        this.imageTagMapper = imageTagMapper;
        this.cosStorage = cosStorage;
        this.uploaderFiller = uploaderFiller;
    }

    public PageData<ImageVO> pageImages(ImageQueryRequest request) {
        Page<Image> page = imageMapper.selectPage(Page.of(request.getCurrent(), request.getSize()),
                request.toQueryWrapper());
        List<ImageVO> records = page.getRecords().stream()
                .map(image -> ImageVO.from(image, cosStorage.signedUrl(image.getCosKey()),
                        cosStorage.signedThumbnailUrl(image.getCosKey())))
                .toList();
        uploaderFiller.fillOwners(records);
        return PageData.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public boolean review(ImageReviewRequest request) {
        if (Image.REVIEW_REJECTED == request.getReviewStatus()
                && !StringUtils.hasText(request.getReviewMessage())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "拒绝时必须填写审核信息");
        }
        if (imageMapper.selectById(request.getId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        Image update = new Image();
        update.setId(request.getId());
        update.setReviewStatus(request.getReviewStatus());
        update.setReviewMessage(request.getReviewMessage());
        update.setReviewerId(CurrentUser.get().getId());
        update.setReviewTime(LocalDateTime.now());
        return imageMapper.updateById(update) > 0;
    }

    public boolean deleteImage(Long id) {
        Image image = imageMapper.selectById(id);
        if (image == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片不存在");
        }
        // COS 删除成功后再软删除数据库记录
        cosStorage.delete(image.getCosKey());
        boolean deleted = imageMapper.deleteById(id) > 0;
        if (deleted) {
            imageTagMapper.decreaseTags(image.tagList());
        }
        return deleted;
    }
}
