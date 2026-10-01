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
    COS_ERROR("COS_ERROR", 502, "对象存储服务异常"),
    PEXELS_ERROR("PEXELS_ERROR", 502, "Pexels 图源服务异常"),
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
