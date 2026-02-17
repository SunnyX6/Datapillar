package com.sunny.datapillar.common.exception;

/**
 * AlreadyExists异常
 * 描述AlreadyExists异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class AlreadyExistsException extends DatapillarRuntimeException {

    public AlreadyExistsException(String message, Object... args) {
        super(message, args);
    }

    public AlreadyExistsException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }
}
