package com.sunny.datapillar.common.exception;

/**
 * ServiceUnavailable异常
 * 描述ServiceUnavailable异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class ServiceUnavailableException extends DatapillarRuntimeException {

    public ServiceUnavailableException(String message, Object... args) {
        super(message, args);
    }

    public ServiceUnavailableException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }
}
