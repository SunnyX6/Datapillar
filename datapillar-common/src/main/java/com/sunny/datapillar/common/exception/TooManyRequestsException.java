package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.util.Map;

/**
 * TooManyRequests异常
 * 描述TooManyRequests异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class TooManyRequestsException extends DatapillarRuntimeException {

    public TooManyRequestsException(String message, Object... args) {
        super(Code.TOO_MANY_REQUESTS, ErrorType.TOO_MANY_REQUESTS, message, args);
    }

    public TooManyRequestsException(Throwable cause, String message, Object... args) {
        super(cause, Code.TOO_MANY_REQUESTS, ErrorType.TOO_MANY_REQUESTS, message, args);
    }

    public TooManyRequestsException(String type, Map<String, String> context, String message, Object... args) {
        super(Code.TOO_MANY_REQUESTS, type, context, false, message, args);
    }

    public TooManyRequestsException(Throwable cause,
                                    String type,
                                    Map<String, String> context,
                                    String message,
                                    Object... args) {
        super(cause, Code.TOO_MANY_REQUESTS, type, context, false, message, args);
    }
}
