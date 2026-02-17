package com.sunny.datapillar.common.utils;

/**
 * 异常Message工具类
 * 提供异常Message通用工具能力
 *
 * @author Sunny
 * @date 2026-01-01
 */
public final class ExceptionMessageUtil {

    private ExceptionMessageUtil() {
    }

    public static String compose(String ignoredMessage, Throwable throwable) {
        return compose(throwable);
    }

    public static String compose(Throwable throwable) {
        if (throwable == null) {
            return "";
        }

        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            Throwable cause = throwable.getCause();
            if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
                message = cause.getMessage();
            }
        }

        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return sanitize(message);
    }

    private static String sanitize(String message) {
        return message.replace("\n", "\\n").replace("\r", "\\r");
    }
}
