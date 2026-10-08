package com.example.cloudpicture.image.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/** 服务间授权请求：payment 支付成功后调用，按 userId 定位空间覆盖写档位 */
@Data
public class SpaceGrantRequest {

    @NotNull(message = "userId 不能为空")
    private Long userId;

    @NotBlank(message = "tier 不能为空")
    private String tier;

    @NotNull(message = "tierExpireTime 不能为空")
    private LocalDateTime tierExpireTime;
}