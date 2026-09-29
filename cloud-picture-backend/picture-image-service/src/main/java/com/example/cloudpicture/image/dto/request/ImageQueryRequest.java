package com.example.cloudpicture.image.dto.request;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.cloudpicture.image.entity.Image;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class ImageQueryRequest {

    @Min(value = 1, message = "页码最小为 1")
    private long current = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private long size = 10;

    private String name;

    private String category;

    private String tag;

    /** 仅管理员使用，0 待审核 / 1 通过 / 2 拒绝 */
    private Integer reviewStatus;

    public LambdaQueryWrapper<Image> toQueryWrapper() {
        return new LambdaQueryWrapper<Image>()
                .like(StringUtils.hasText(name), Image::getName, name)
                .eq(StringUtils.hasText(category), Image::getCategory, category)
                .like(StringUtils.hasText(tag), Image::getTags, tag)
                .eq(reviewStatus != null, Image::getReviewStatus, reviewStatus)
                .orderByDesc(Image::getCreateTime);
    }
}