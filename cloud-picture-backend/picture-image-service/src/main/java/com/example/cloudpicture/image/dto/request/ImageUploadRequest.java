package com.example.cloudpicture.image.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class ImageUploadRequest {

    @Size(max = 256, message = "图片名称长度不能超过 256")
    private String name;

    @Size(max = 512, message = "简介长度不能超过 512")
    private String introduction;

    @Size(max = 64, message = "分类长度不能超过 64")
    private String category;

    private List<String> tags;
}