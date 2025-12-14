package com.sunny.admin.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Web Admin 统一响应格式
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebAdminResponse<T> {

    private String code;
    private String message;
    private T data;

    public boolean isSuccess() {
        return WebAdminErrorCode.SUCCESS.getCode().equals(code);
    }

    public static <T> WebAdminResponse<T> ok(T data) {
        return new WebAdminResponse<>(WebAdminErrorCode.SUCCESS.getCode(), WebAdminErrorCode.SUCCESS.getMessageTemplate(), data);
    }

    public static <T> WebAdminResponse<T> ok() {
        return new WebAdminResponse<>(WebAdminErrorCode.SUCCESS.getCode(), WebAdminErrorCode.SUCCESS.getMessageTemplate(), null);
    }

    public static <T> WebAdminResponse<T> ok(String message, T data) {
        return new WebAdminResponse<>(WebAdminErrorCode.SUCCESS.getCode(), message, data);
    }

    public static <T> WebAdminResponse<T> error(String message) {
        return new WebAdminResponse<>(WebAdminErrorCode.ERROR.getCode(), message, null);
    }

    public static <T> WebAdminResponse<T> error(WebAdminErrorCode errorCode, Object... args) {
        return new WebAdminResponse<>(errorCode.getCode(), errorCode.formatMessage(args), null);
    }

    public static <T> WebAdminResponse<T> error(String code, String message) {
        return new WebAdminResponse<>(code, message, null);
    }

    public static <T> WebAdminResponse<T> validationError(String message) {
        return new WebAdminResponse<>(WebAdminErrorCode.VALIDATION_ERROR.getCode(), message, null);
    }

    public static <T> WebAdminResponse<T> unauthorized(String message) {
        return new WebAdminResponse<>(WebAdminErrorCode.UNAUTHORIZED.getCode(), message, null);
    }

    public static <T> WebAdminResponse<T> forbidden(String message) {
        return new WebAdminResponse<>(WebAdminErrorCode.FORBIDDEN.getCode(), message, null);
    }

    public static <T> WebAdminResponse<T> notFound(String message) {
        return new WebAdminResponse<>(WebAdminErrorCode.RESOURCE_NOT_FOUND.getCode(), message, null);
    }

    public static <T> WebAdminResponse<T> success(T data) {
        return ok(data);
    }

    public static <T> WebAdminResponse<T> success() {
        return ok();
    }
}
