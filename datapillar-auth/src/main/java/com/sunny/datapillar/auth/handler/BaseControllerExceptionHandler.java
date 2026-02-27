package com.sunny.datapillar.auth.handler;

import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.exception.ExceptionMapper;
import com.sunny.datapillar.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * BaseController异常处理器
 * 负责BaseController异常处理流程与结果输出
 *
 * @author Sunny
 * @date 2026-01-01
 */
public abstract class BaseControllerExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BaseControllerExceptionHandler.class);

    @ExceptionHandler(DatapillarRuntimeException.class)
    public ErrorResponse handleDatapillarRuntimeException(DatapillarRuntimeException exception,
                                                          HttpServletResponse response) {
        return buildErrorResponse(exception, response);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ErrorResponse handleValidationException(Exception exception, HttpServletResponse response) {
        Map<String, String> errors = new HashMap<>();

        if (exception instanceof MethodArgumentNotValidException ex) {
            ex.getBindingResult().getAllErrors().forEach((error) -> errors.put(resolveErrorKey(error), error.getDefaultMessage()));
        } else if (exception instanceof BindException ex) {
            ex.getBindingResult().getAllErrors().forEach((error) -> errors.put(resolveErrorKey(error), error.getDefaultMessage()));
        }

        String message = errors.isEmpty() ? "参数验证失败" : errors.toString();
        return handleDatapillarRuntimeException(new com.sunny.datapillar.common.exception.BadRequestException(exception, message), response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ErrorResponse handleIllegalArgumentException(IllegalArgumentException exception,
                                                        HttpServletResponse response) {
        String message = exception.getMessage() == null ? "参数错误" : exception.getMessage();
        return handleDatapillarRuntimeException(new com.sunny.datapillar.common.exception.BadRequestException(exception, message), response);
    }

    @ExceptionHandler(Exception.class)
    public ErrorResponse handleException(Exception exception, HttpServletResponse response) {
        return buildErrorResponse(exception, response);
    }

    private String resolveErrorKey(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField();
        }
        return error.getObjectName();
    }

    private ErrorResponse buildErrorResponse(Throwable throwable, HttpServletResponse response) {
        ExceptionMapper.ExceptionDetail detail = ExceptionMapper.resolve(throwable);
        if (detail.serverError()) {
            log.error("服务异常: type={}, message={}", detail.type(), detail.message(), throwable);
        } else {
            log.warn("请求异常: type={}, message={}", detail.type(), detail.message(), throwable);
        }

        response.setStatus(detail.httpStatus());
        return ErrorResponse.of(
                detail.errorCode(),
                detail.type(),
                detail.message(),
                detail.traceId());
    }
}
