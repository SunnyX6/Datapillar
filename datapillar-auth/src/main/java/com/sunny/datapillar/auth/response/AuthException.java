package com.sunny.datapillar.auth.response;

import lombok.Getter;

/**
 * 认证模块异常
 */
@Getter
public class AuthException extends RuntimeException {

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误码枚举
     */
    private final AuthErrorCode errorCode;

    /**
     * 使用错误码枚举创建异常
     */
    public AuthException(AuthErrorCode errorCode, Object... args) {
        super(errorCode.formatMessage(args));
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    /**
     * 使用错误码枚举创建异常(带原因)
     */
    public AuthException(AuthErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.formatMessage(args), cause);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }
}
