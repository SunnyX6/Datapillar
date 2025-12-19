package com.sunny.datapillar.admin.response;

import lombok.Getter;

/**
 * Web Admin 模块异常
 */
@Getter
public class WebAdminException extends RuntimeException {

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误码枚举
     */
    private final WebAdminErrorCode errorCode;

    /**
     * 使用错误码枚举创建异常
     */
    public WebAdminException(WebAdminErrorCode errorCode, Object... args) {
        super(errorCode.formatMessage(args));
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    /**
     * 使用错误码枚举创建异常(带原因)
     */
    public WebAdminException(WebAdminErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.formatMessage(args), cause);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }
}
