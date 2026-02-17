package com.sunny.datapillar.common.exception;

import com.sunny.datapillar.common.constant.ErrorConstants;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

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

        if (target instanceof BadRequestException || target instanceof IllegalArgumentException) {
            return new ExceptionDetail(400, ErrorConstants.ILLEGAL_ARGUMENTS_CODE, type, message, stack, false);
        }
        if (target instanceof UnauthorizedException) {
            return new ExceptionDetail(401, ErrorConstants.UNAUTHORIZED_CODE, type, message, stack, false);
        }
        if (target instanceof ForbiddenException) {
            return new ExceptionDetail(403, ErrorConstants.FORBIDDEN_CODE, type, message, stack, false);
        }
        if (target instanceof NotFoundException) {
            return new ExceptionDetail(404, ErrorConstants.NOT_FOUND_CODE, type, message, stack, false);
        }
        if (target instanceof AlreadyExistsException) {
            return new ExceptionDetail(409, ErrorConstants.ALREADY_EXISTS_CODE, type, message, stack, false);
        }
        if (target instanceof ConflictException) {
            return new ExceptionDetail(409, ErrorConstants.CONFLICT_CODE, type, message, stack, false);
        }
        if (target instanceof TooManyRequestsException) {
            return new ExceptionDetail(429, ErrorConstants.TOO_MANY_REQUESTS_CODE, type, message, stack, false);
        }
        if (target instanceof UnsupportedOperationException) {
            return new ExceptionDetail(405, ErrorConstants.UNSUPPORTED_OPERATION_CODE, type, message, stack, false);
        }
        if (target instanceof ConnectionFailedException) {
            return new ExceptionDetail(502, ErrorConstants.CONNECTION_FAILED_CODE, type, message, stack, true);
        }
        if (target instanceof ServiceUnavailableException) {
            return new ExceptionDetail(503, ErrorConstants.SERVICE_UNAVAILABLE_CODE, type, message, stack, true);
        }

        return new ExceptionDetail(500, ErrorConstants.INTERNAL_ERROR_CODE, type, message, stack, true);
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
            boolean serverError) {
    }
}
