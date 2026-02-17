package com.sunny.datapillar.common.exception;

/**
 * Forbidden异常
 * 描述Forbidden异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class ForbiddenException extends DatapillarRuntimeException {

    public ForbiddenException(String message, Object... args) {
        super(message, args);
    }

    public ForbiddenException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }
}
