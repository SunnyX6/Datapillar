package com.sunny.datapillar.workbench.web.handler;

import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import com.sunny.datapillar.common.exception.SystemException;
import com.sunny.datapillar.workbench.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一异常处理基类
 */
public abstract class BaseGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BaseGlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Object> handleBusinessException(BusinessException e, HttpServletResponse response) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        response.setStatus(e.getErrorCode().getHttpStatus());
        return ApiResponse.error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(SystemException.class)
    public ApiResponse<Object> handleSystemException(SystemException e, HttpServletResponse response) {
        log.error("系统异常: {}", e.getMessage(), e);
        return buildInternalError(response);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<Object> handleDuplicateKeyException(DuplicateKeyException e, HttpServletResponse response) {
        log.warn("唯一键冲突: {}", e.getMessage());
        ErrorCode errorCode = resolveDuplicateKeyErrorCode(e);
        String message = resolveDuplicateKeyMessage(e, errorCode);
        response.setStatus(errorCode.getHttpStatus());
        return ApiResponse.error(errorCode, message);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ApiResponse<Object> handleValidationException(Exception e, HttpServletResponse response) {
        log.warn("参数校验失败: {}", e.getMessage());
        Map<String, String> errors = new HashMap<>();

        if (e instanceof MethodArgumentNotValidException ex) {
            ex.getBindingResult().getAllErrors().forEach((error) -> {
                String fieldName = ((FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                errors.put(fieldName, errorMessage);
            });
        } else if (e instanceof BindException ex) {
            ex.getBindingResult().getAllErrors().forEach((error) -> {
                String fieldName = ((FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                errors.put(fieldName, errorMessage);
            });
        }

        ErrorCode errorCode = getValidationErrorCode();
        response.setStatus(errorCode.getHttpStatus());
        String message = errors.isEmpty() ? errorCode.getMessageTemplate() : errors.toString();
        return ApiResponse.error(errorCode, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Object> handleIllegalArgumentException(IllegalArgumentException e, HttpServletResponse response) {
        log.warn("参数错误: {}", e.getMessage());
        ErrorCode errorCode = getInvalidArgumentErrorCode();
        response.setStatus(errorCode.getHttpStatus());
        String message = e.getMessage() == null ? errorCode.getMessageTemplate() : e.getMessage();
        return ApiResponse.error(errorCode, message);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Object> handleException(Exception e, HttpServletResponse response) {
        log.error("未捕获异常", e);
        return buildInternalError(response);
    }

    protected abstract ErrorCode getValidationErrorCode();

    protected abstract ErrorCode getInvalidArgumentErrorCode();

    protected abstract ErrorCode getDuplicateKeyErrorCode();

    protected ErrorCode resolveDuplicateKeyErrorCode(DuplicateKeyException e) {
        return getDuplicateKeyErrorCode();
    }

    protected String resolveDuplicateKeyMessage(DuplicateKeyException e, ErrorCode errorCode) {
        return errorCode.getMessageTemplate();
    }

    protected ErrorCode getInternalErrorCode() {
        return ErrorCode.COMMON_INTERNAL_ERROR;
    }

    private ApiResponse<Object> buildInternalError(HttpServletResponse response) {
        ErrorCode errorCode = getInternalErrorCode();
        response.setStatus(errorCode.getHttpStatus());
        return ApiResponse.error(errorCode);
    }
}
