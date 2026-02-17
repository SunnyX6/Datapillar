package com.sunny.datapillar.common.exception;

/**
 * Conflict异常
 * 描述Conflict异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class ConflictException extends DatapillarRuntimeException {

    public ConflictException(String message, Object... args) {
        super(message, args);
    }

    public ConflictException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }
}
