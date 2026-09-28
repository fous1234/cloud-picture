package com.example.cloudpicture.common.api;

import com.example.cloudpicture.common.exception.ErrorCode;
import lombok.Data;

/**
 * 统一响应结构：{ code, message, data }
 */
@Data
public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = ErrorCode.OK.getCode();
        response.message = ErrorCode.OK.getMessage();
        response.data = data;
        return response;
    }

    public static <T> ApiResponse<T> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.code = errorCode.getCode();
        response.message = message == null || message.isBlank() ? errorCode.getMessage() : message;
        return response;
    }
}