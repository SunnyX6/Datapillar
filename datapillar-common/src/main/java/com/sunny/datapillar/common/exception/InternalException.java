package com.sunny.datapillar.common.exception;

/**
 * Internal异常
 * 描述Internal异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class InternalException extends DatapillarRuntimeException {

    public InternalException(String message, Object... args) {
        super(message, args);
    }

    public InternalException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }
}
