package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.util.Map;

/**
 * Bad请求异常
 * 描述Bad请求异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class BadRequestException extends DatapillarRuntimeException {

    public BadRequestException(String message, Object... args) {
        super(Code.BAD_REQUEST, ErrorType.BAD_REQUEST, message, args);
    }

    public BadRequestException(Throwable cause, String message, Object... args) {
        super(cause, Code.BAD_REQUEST, ErrorType.BAD_REQUEST, message, args);
    }

    public BadRequestException(String type, Map<String, String> context, String message, Object... args) {
        super(Code.BAD_REQUEST, type, context, false, message, args);
    }

    public BadRequestException(Throwable cause,
                               String type,
                               Map<String, String> context,
                               String message,
                               Object... args) {
        super(cause, Code.BAD_REQUEST, type, context, false, message, args);
    }
}
