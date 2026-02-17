package com.sunny.datapillar.common.exception;

/**
 * ConnectionFailed异常
 * 描述ConnectionFailed异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class ConnectionFailedException extends DatapillarRuntimeException {

    public ConnectionFailedException(String message, Object... args) {
        super(message, args);
    }

    public ConnectionFailedException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }
}
