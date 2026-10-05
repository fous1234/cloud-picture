package com.example.cloudpicture.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 服务间内部审核请求：imageUrl 为 image-service 生成的短期 COS 签名地址，
 * ownerId 用于限流计费归属
 */
@Data
public class ModerationRequest {

    @NotBlank(message = "图片地址不能为空")
    private String imageUrl;

    @NotNull(message = "上传者 id 不能为空")
    private Long ownerId;
}
