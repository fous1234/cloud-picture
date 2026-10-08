package com.example.cloudpicture.payment.dto.request;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 服务间授权请求体：对应 image-service 的 SpaceGrantRequest */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpaceGrantPayload {

    private Long userId;
    private String tier;
    private LocalDateTime tierExpireTime;
}