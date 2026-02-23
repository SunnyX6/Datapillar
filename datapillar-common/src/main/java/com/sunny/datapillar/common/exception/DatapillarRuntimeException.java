package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.util.Map;

/**
 * Runtime异常
 * 描述Runtime异常语义与错误上下文
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class DatapillarRuntimeException extends RuntimeException {

    private final int code;
    private final String type;
    private final Map<String, String> context;
    private final boolean retryable;

    protected DatapillarRuntimeException(String message) {
        this(Code.INTERNAL_ERROR, ErrorType.INTERNAL_ERROR, null, false, null, message);
    }

    protected DatapillarRuntimeException(String message, Object... args) {
        this(Code.INTERNAL_ERROR, ErrorType.INTERNAL_ERROR, null, false, null, format(message, args));
    }

    protected DatapillarRuntimeException(Throwable cause, String message, Object... args) {
        this(Code.INTERNAL_ERROR, ErrorType.INTERNAL_ERROR, null, false, cause, format(message, args));
    }

    protected DatapillarRuntimeException(int code,
                                         String type,
                                         String message,
                                         Object... args) {
        this(code, type, null, false, null, format(message, args));
    }

    protected DatapillarRuntimeException(int code,
                                         String type,
                                         Map<String, String> context,
                                         boolean retryable,
                                         String message,
                                         Object... args) {
        this(code, type, context, retryable, null, format(message, args));
    }

    protected DatapillarRuntimeException(Throwable cause,
                                         int code,
                                         String type,
                                         String message,
                                         Object... args) {
        this(code, type, null, false, cause, format(message, args));
    }

    protected DatapillarRuntimeException(Throwable cause,
                                         int code,
                                         String type,
                                         Map<String, String> context,
                                         boolean retryable,
                                         String message,
                                         Object... args) {
        this(code, type, context, retryable, cause, format(message, args));
    }

    private DatapillarRuntimeException(int code,
                                       String type,
                                       Map<String, String> context,
                                       boolean retryable,
                                       Throwable cause,
                                       String message) {
        super(message, cause);
        this.code = code;
        this.type = type;
        this.context = context == null ? Map.of() : Map.copyOf(context);
        this.retryable = retryable;
    }

    public int getCode() {
        return code;
    }

    public String getType() {
        return type;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public boolean isRetryable() {
        return retryable;
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
