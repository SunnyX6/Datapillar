package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.error.ErrorCode;
import lombok.Getter;

/**
 * 统一业务异常
 */
public class BusinessException extends RuntimeException {

    @Getter
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.formatMessage(args));
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.formatMessage(args), cause);
        this.errorCode = errorCode;
    }
}
