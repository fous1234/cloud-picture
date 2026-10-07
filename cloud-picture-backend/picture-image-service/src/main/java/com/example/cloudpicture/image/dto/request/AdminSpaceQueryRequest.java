package com.example.cloudpicture.image.dto.request;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.cloudpicture.image.entity.PrivateSpace;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class AdminSpaceQueryRequest {

    @Min(value = 1, message = "页码最小为 1")
    private long current = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private long size = 10;

    private String name;

    public LambdaQueryWrapper<PrivateSpace> toQueryWrapper() {
        return new LambdaQueryWrapper<PrivateSpace>()
                .like(StringUtils.hasText(name), PrivateSpace::getName, name)
                .orderByDesc(PrivateSpace::getCreateTime);
    }
}