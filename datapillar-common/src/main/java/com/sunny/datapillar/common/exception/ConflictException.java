package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.util.Map;

/**
 * Conflict异常
 * 描述Conflict异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class ConflictException extends DatapillarRuntimeException {

    public ConflictException(String message, Object... args) {
        super(Code.CONFLICT, ErrorType.CONFLICT, message, args);
    }

    public ConflictException(Throwable cause, String message, Object... args) {
        super(cause, Code.CONFLICT, ErrorType.CONFLICT, message, args);
    }

    public ConflictException(String type, Map<String, String> context, String message, Object... args) {
        super(Code.CONFLICT, type, context, false, message, args);
    }

    public ConflictException(Throwable cause,
                             String type,
                             Map<String, String> context,
                             String message,
                             Object... args) {
        super(cause, Code.CONFLICT, type, context, false, message, args);
    }
}
