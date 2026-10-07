package com.example.cloudpicture.image.dto.request;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.cloudpicture.image.entity.SpaceImage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class SpaceImageQueryRequest {

    @Min(value = 1, message = "页码最小为 1")
    private long current = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private long size = 10;

    private String name;

    private String category;

    private String tag;

    public LambdaQueryWrapper<SpaceImage> toQueryWrapper() {
        return new LambdaQueryWrapper<SpaceImage>()
                .like(StringUtils.hasText(name), SpaceImage::getName, name)
                .eq(StringUtils.hasText(category), SpaceImage::getCategory, category)
                .like(StringUtils.hasText(tag), SpaceImage::getTags, tag)
                .orderByDesc(SpaceImage::getCreateTime);
    }
}