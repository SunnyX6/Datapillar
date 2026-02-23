package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.Code;
import com.sunny.datapillar.common.constant.ErrorType;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;

/**
 * 异常Mapper
 * 负责异常数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
public final class ExceptionMapper {

    private static final String DEFAULT_INTERNAL_MESSAGE = "服务器内部错误";

    private ExceptionMapper() {
    }

    public static ExceptionDetail resolve(Throwable throwable) {
        Throwable target = throwable == null ? new InternalException(DEFAULT_INTERNAL_MESSAGE) : throwable;
        String message = resolveMessage(target);
        String type = target.getClass().getSimpleName();
        List<String> stack = getStackTrace(target);
        String traceId = resolveTraceId();

        if (target instanceof DatapillarRuntimeException runtimeException) {
            return new ExceptionDetail(
                    runtimeException.getCode(),
                    runtimeException.getCode(),
                    runtimeException.getType(),
                    message,
                    stack,
                    runtimeException.getContext(),
                    traceId,
                    runtimeException.isRetryable(),
                    resolveServerError(runtimeException));
        }
        if (target instanceof IllegalArgumentException) {
            return buildDetail(
                    Code.BAD_REQUEST,
                    ErrorType.BAD_REQUEST,
                    message,
                    stack,
                    Map.of(),
                    traceId,
                    false,
                    false);
        }
        if (target instanceof UnsupportedOperationException) {
            return buildDetail(
                    Code.METHOD_NOT_ALLOWED,
                    ErrorType.METHOD_NOT_ALLOWED,
                    message,
                    stack,
                    Map.of(),
                    traceId,
                    false,
                    false);
        }
        return buildDetail(
                Code.INTERNAL_ERROR,
                ErrorType.INTERNAL_ERROR,
                message,
                stack,
                Map.of(),
                traceId,
                false,
                true);
    }

    private static ExceptionDetail buildDetail(int code,
                                               String type,
                                               String message,
                                               List<String> stack,
                                               Map<String, String> context,
                                               String traceId,
                                               boolean retryable,
                                               boolean serverError) {
        return new ExceptionDetail(
                code,
                code,
                type,
                message,
                stack,
                context == null ? Map.of() : Map.copyOf(context),
                traceId,
                retryable,
                serverError);
    }

    private static String resolveMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }

        Throwable cause = throwable.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }

        return DEFAULT_INTERNAL_MESSAGE;
    }

    private static boolean resolveServerError(DatapillarRuntimeException exception) {
        if (exception == null) {
            return true;
        }
        if (ErrorType.REQUIRED.equals(exception.getType())) {
            return false;
        }
        return exception.getCode() >= Code.INTERNAL_ERROR;
    }

    private static String resolveTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        String fallback = MDC.get("trace_id");
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    private static List<String> getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            throwable.printStackTrace(pw);
        }

        return Arrays.asList(sw.toString().split("\\n"));
    }

    public record ExceptionDetail(
            int httpStatus,
            int errorCode,
            String type,
            String message,
            List<String> stack,
            Map<String, String> context,
            String traceId,
            boolean retryable,
            boolean serverError) {
    }
}
