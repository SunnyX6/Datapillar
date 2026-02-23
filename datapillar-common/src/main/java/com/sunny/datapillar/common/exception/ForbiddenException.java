package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.util.Map;

/**
 * Forbidden异常
 * 描述Forbidden异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class ForbiddenException extends DatapillarRuntimeException {

    public ForbiddenException(String message, Object... args) {
        super(Code.FORBIDDEN, ErrorType.FORBIDDEN, message, args);
    }

    public ForbiddenException(Throwable cause, String message, Object... args) {
        super(cause, Code.FORBIDDEN, ErrorType.FORBIDDEN, message, args);
    }

    public ForbiddenException(String type, Map<String, String> context, String message, Object... args) {
        super(Code.FORBIDDEN, type, context, false, message, args);
    }

    public ForbiddenException(Throwable cause,
                              String type,
                              Map<String, String> context,
                              String message,
                              Object... args) {
        super(cause, Code.FORBIDDEN, type, context, false, message, args);
    }
}
