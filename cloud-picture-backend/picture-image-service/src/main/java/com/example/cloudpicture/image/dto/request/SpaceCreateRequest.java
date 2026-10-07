package com.example.cloudpicture.image.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SpaceCreateRequest {

    /** 不传时使用默认空间名 */
    @Size(max = 64, message = "空间名称长度不能超过 64")
    private String name;
}