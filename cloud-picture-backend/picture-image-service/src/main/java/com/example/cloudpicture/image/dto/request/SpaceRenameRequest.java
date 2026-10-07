package com.example.cloudpicture.image.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SpaceRenameRequest {

    @NotBlank(message = "空间名称不能为空")
    @Size(max = 64, message = "空间名称长度不能超过 64")
    private String name;
}