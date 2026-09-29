package com.example.cloudpicture.common.exception;

import lombok.Getter;

/**
 * 业务异常，由 GlobalExceptionHandler 统一转换为响应
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}