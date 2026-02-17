package com.sunny.datapillar.common.exception;

/**
 * Unauthorized异常
 * 描述Unauthorized异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class UnauthorizedException extends DatapillarRuntimeException {

    public UnauthorizedException(String message, Object... args) {
        super(message, args);
    }

    public UnauthorizedException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }
}
