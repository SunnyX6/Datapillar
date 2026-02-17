package com.sunny.datapillar.common.exception;

/**
 * NotFound异常
 * 描述NotFound异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class NotFoundException extends DatapillarRuntimeException {

    public NotFoundException(String message, Object... args) {
        super(message, args);
    }

    public NotFoundException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }
}
