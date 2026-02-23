package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.util.Map;

/**
 * Internal异常
 * 描述Internal异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class InternalException extends DatapillarRuntimeException {

    public InternalException(String message, Object... args) {
        super(Code.INTERNAL_ERROR, ErrorType.INTERNAL_ERROR, null, false, message, args);
    }

    public InternalException(Throwable cause, String message, Object... args) {
        super(cause, Code.INTERNAL_ERROR, ErrorType.INTERNAL_ERROR, null, false, message, args);
    }

    public InternalException(String type, Map<String, String> context, String message, Object... args) {
        super(Code.INTERNAL_ERROR, type, context, false, message, args);
    }

    public InternalException(Throwable cause,
                             String type,
                             Map<String, String> context,
                             String message,
                             Object... args) {
        super(cause, Code.INTERNAL_ERROR, type, context, false, message, args);
    }
}
