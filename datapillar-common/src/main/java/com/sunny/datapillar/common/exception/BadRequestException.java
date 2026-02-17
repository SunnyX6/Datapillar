package com.sunny.datapillar.common.exception;

/**
 * Bad请求异常
 * 描述Bad请求异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class BadRequestException extends DatapillarRuntimeException {

    public BadRequestException(String message, Object... args) {
        super(message, args);
    }

    public BadRequestException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }
}
