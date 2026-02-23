package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.util.Map;

/**
 * AlreadyExists异常
 * 描述AlreadyExists异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class AlreadyExistsException extends DatapillarRuntimeException {

    public AlreadyExistsException(String message, Object... args) {
        super(Code.CONFLICT, ErrorType.ALREADY_EXISTS, message, args);
    }

    public AlreadyExistsException(Throwable cause, String message, Object... args) {
        super(cause, Code.CONFLICT, ErrorType.ALREADY_EXISTS, message, args);
    }

    public AlreadyExistsException(String type, Map<String, String> context, String message, Object... args) {
        super(Code.CONFLICT, type, context, false, message, args);
    }

    public AlreadyExistsException(Throwable cause,
                                  String type,
                                  Map<String, String> context,
                                  String message,
                                  Object... args) {
        super(cause, Code.CONFLICT, type, context, false, message, args);
    }
}
