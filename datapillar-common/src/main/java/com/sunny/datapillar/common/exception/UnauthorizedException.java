package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.util.Map;

/**
 * Unauthorized异常
 * 描述Unauthorized异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class UnauthorizedException extends DatapillarRuntimeException {

    public UnauthorizedException(String message, Object... args) {
        super(Code.UNAUTHORIZED, ErrorType.UNAUTHORIZED, message, args);
    }

    public UnauthorizedException(Throwable cause, String message, Object... args) {
        super(cause, Code.UNAUTHORIZED, ErrorType.UNAUTHORIZED, message, args);
    }

    public UnauthorizedException(String type, Map<String, String> context, String message, Object... args) {
        super(Code.UNAUTHORIZED, type, context, false, message, args);
    }

    public UnauthorizedException(Throwable cause,
                                 String type,
                                 Map<String, String> context,
                                 String message,
                                 Object... args) {
        super(cause, Code.UNAUTHORIZED, type, context, false, message, args);
    }
}
