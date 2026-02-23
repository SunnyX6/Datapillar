package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.util.Map;

/**
 * ServiceUnavailable异常
 * 描述ServiceUnavailable异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class ServiceUnavailableException extends DatapillarRuntimeException {

    public ServiceUnavailableException(String message, Object... args) {
        super(Code.SERVICE_UNAVAILABLE, ErrorType.SERVICE_UNAVAILABLE, null, true, message, args);
    }

    public ServiceUnavailableException(Throwable cause, String message, Object... args) {
        super(cause, Code.SERVICE_UNAVAILABLE, ErrorType.SERVICE_UNAVAILABLE, null, true, message, args);
    }

    public ServiceUnavailableException(String type, Map<String, String> context, String message, Object... args) {
        super(Code.SERVICE_UNAVAILABLE, type, context, true, message, args);
    }

    public ServiceUnavailableException(Throwable cause,
                                       String type,
                                       Map<String, String> context,
                                       String message,
                                       Object... args) {
        super(cause, Code.SERVICE_UNAVAILABLE, type, context, true, message, args);
    }
}
