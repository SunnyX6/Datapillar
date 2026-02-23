package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.util.Map;

/**
 * Required异常
 * 描述前置条件未满足异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class RequiredException extends DatapillarRuntimeException {

    public RequiredException(String message, Object... args) {
        super(Code.SERVICE_UNAVAILABLE, ErrorType.REQUIRED, null, false, message, args);
    }

    public RequiredException(Throwable cause, String message, Object... args) {
        super(cause, Code.SERVICE_UNAVAILABLE, ErrorType.REQUIRED, null, false, message, args);
    }

    public RequiredException(String type, Map<String, String> context, String message, Object... args) {
        super(Code.SERVICE_UNAVAILABLE, type, context, false, message, args);
    }

    public RequiredException(Throwable cause,
                             String type,
                             Map<String, String> context,
                             String message,
                             Object... args) {
        super(cause, Code.SERVICE_UNAVAILABLE, type, context, false, message, args);
    }
}
