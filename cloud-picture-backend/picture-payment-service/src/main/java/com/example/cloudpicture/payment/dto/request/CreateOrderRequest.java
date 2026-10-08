package com.example.cloudpicture.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateOrderRequest {

    /** 目标档位：PRO / MAX（FREE 无需购买） */
    @NotBlank(message = "档位不能为空")
    private String tier;
}