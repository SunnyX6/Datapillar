package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.util.Map;

/**
 * NotFound异常
 * 描述NotFound异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class NotFoundException extends DatapillarRuntimeException {

    public NotFoundException(String message, Object... args) {
        super(Code.NOT_FOUND, ErrorType.NOT_FOUND, message, args);
    }

    public NotFoundException(Throwable cause, String message, Object... args) {
        super(cause, Code.NOT_FOUND, ErrorType.NOT_FOUND, message, args);
    }

    public NotFoundException(String type, Map<String, String> context, String message, Object... args) {
        super(Code.NOT_FOUND, type, context, false, message, args);
    }

    public NotFoundException(Throwable cause,
                             String type,
                             Map<String, String> context,
                             String message,
                             Object... args) {
        super(cause, Code.NOT_FOUND, type, context, false, message, args);
    }
}
