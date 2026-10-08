package com.example.cloudpicture.payment.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 管理端订单查询：只读，提供 userId / status / tier 三个筛选 */
@Data
public class AdminOrderQueryRequest {

    @Min(value = 1, message = "页码最小为 1")
    private long current = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    private long size = 10;

    private Long userId;

    private String status;

    private String tier;
}