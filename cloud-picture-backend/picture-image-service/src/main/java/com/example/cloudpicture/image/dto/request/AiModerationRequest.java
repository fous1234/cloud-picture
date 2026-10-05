package com.example.cloudpicture.image.dto.request;

import lombok.Data;

/**
 * 服务间内部审核请求（与 ai-service 的 ModerationRequest 字段对应）
 */
@Data
public class AiModerationRequest {

    private String imageUrl;

    private Long ownerId;
}
