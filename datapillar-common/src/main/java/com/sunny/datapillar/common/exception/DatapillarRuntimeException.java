package com.sunny.datapillar.common.exception;

/**
 * Runtime异常
 * 描述Runtime异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class DatapillarRuntimeException extends RuntimeException {

    public DatapillarRuntimeException(String message) {
        super(message);
    }

    public DatapillarRuntimeException(String message, Object... args) {
        super(format(message, args));
    }

    public DatapillarRuntimeException(Throwable cause, String message, Object... args) {
        super(format(message, args), cause);
    }

    private static String format(String message, Object... args) {
        if (message == null) {
            return "";
        }
        if (args == null || args.length == 0) {
            return message;
        }
        return String.format(message, args);
    }
}
