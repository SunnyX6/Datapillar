package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.util.Map;

/**
 * ConnectionFailed异常
 * 描述ConnectionFailed异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class ConnectionFailedException extends DatapillarRuntimeException {

    public ConnectionFailedException(String message, Object... args) {
        super(Code.BAD_GATEWAY, ErrorType.BAD_GATEWAY, null, true, message, args);
    }

    public ConnectionFailedException(Throwable cause, String message, Object... args) {
        super(cause, Code.BAD_GATEWAY, ErrorType.BAD_GATEWAY, null, true, message, args);
    }

    public ConnectionFailedException(String type, Map<String, String> context, String message, Object... args) {
        super(Code.BAD_GATEWAY, type, context, true, message, args);
    }

    public ConnectionFailedException(Throwable cause,
                                     String type,
                                     Map<String, String> context,
                                     String message,
                                     Object... args) {
        super(cause, Code.BAD_GATEWAY, type, context, true, message, args);
    }
}
