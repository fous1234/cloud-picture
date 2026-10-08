package com.example.cloudpicture.common.exception;

import lombok.Getter;

/**
 * 稳定错误码：code 给前端判断，httpStatus 决定响应状态码
 */
@Getter
public enum ErrorCode {

    OK("OK", 200, "success"),
    PARAMS_ERROR("PARAMS_ERROR", 400, "请求参数错误"),
    NOT_LOGIN("NOT_LOGIN", 401, "未登录"),
    NO_AUTH("NO_AUTH", 403, "无权限"),
    NOT_FOUND("NOT_FOUND", 404, "资源不存在"),
    ACCOUNT_CONFLICT("ACCOUNT_CONFLICT", 409, "账号已存在"),
    SPACE_EXISTS("SPACE_EXISTS", 409, "私有空间已创建"),
    SPACE_QUOTA_EXCEEDED("SPACE_QUOTA_EXCEEDED", 400, "私有空间配额已满"),
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", 404, "订单不存在"),
    ORDER_ALREADY_PAID("ORDER_ALREADY_PAID", 409, "订单已支付"),
    ORDER_STATUS_INVALID("ORDER_STATUS_INVALID", 409, "订单状态不允许该操作"),
    PLAN_NOT_PURCHASABLE("PLAN_NOT_PURCHASABLE", 409, "该套餐当前不可购买"),
    PAY_CHANNEL_ERROR("PAY_CHANNEL_ERROR", 502, "支付渠道异常"),
    COS_ERROR("COS_ERROR", 502, "对象存储服务异常"),
    PEXELS_ERROR("PEXELS_ERROR", 502, "Pexels 图源服务异常"),
    AI_UNAVAILABLE("AI_UNAVAILABLE", 503, "AI 服务暂不可用"),
    AI_RATE_LIMIT("AI_RATE_LIMIT", 429, "AI 请求过于频繁，请稍后再试"),
    SYSTEM_ERROR("SYSTEM_ERROR", 500, "系统内部错误");

    private final String code;
    private final int httpStatus;
    private final String message;

    ErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
