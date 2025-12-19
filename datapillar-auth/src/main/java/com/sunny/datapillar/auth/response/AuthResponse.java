package com.sunny.datapillar.auth.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证模块统一响应格式
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse<T> {

    private String code;
    private String message;
    private T data;

    public boolean isSuccess() {
        return AuthErrorCode.SUCCESS.getCode().equals(code);
    }

    public static <T> AuthResponse<T> ok(T data) {
        return new AuthResponse<>(AuthErrorCode.SUCCESS.getCode(), AuthErrorCode.SUCCESS.getMessageTemplate(), data);
    }

    public static <T> AuthResponse<T> ok() {
        return new AuthResponse<>(AuthErrorCode.SUCCESS.getCode(), AuthErrorCode.SUCCESS.getMessageTemplate(), null);
    }

    public static <T> AuthResponse<T> error(String message) {
        return new AuthResponse<>(AuthErrorCode.ERROR.getCode(), message, null);
    }

    public static <T> AuthResponse<T> error(AuthErrorCode errorCode, Object... args) {
        return new AuthResponse<>(errorCode.getCode(), errorCode.formatMessage(args), null);
    }

    public static <T> AuthResponse<T> error(String code, String message) {
        return new AuthResponse<>(code, message, null);
    }

    public static <T> AuthResponse<T> unauthorized(String message) {
        return new AuthResponse<>(AuthErrorCode.UNAUTHORIZED.getCode(), message, null);
    }

    public static <T> AuthResponse<T> success(T data) {
        return ok(data);
    }

    public static <T> AuthResponse<T> success() {
        return ok();
    }
}
