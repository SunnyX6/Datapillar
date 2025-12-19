package com.sunny.datapillar.admin.config;

import com.sunny.datapillar.admin.response.WebAdminException;
import com.sunny.datapillar.admin.response.WebAdminResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Web Admin 异常处理器
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(WebAdminException.class)
    public WebAdminResponse<Object> handleWebAdminException(WebAdminException e) {
        log.warn("WebAdmin exception: code={}, message={}", e.getCode(), e.getMessage());
        return WebAdminResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理唯一键冲突异常
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public WebAdminResponse<Object> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("Duplicate key exception: {}", e.getMessage());

        String message = e.getMessage();
        String errorCode;
        String errorMessage;

        if (message != null && message.contains("template_parameters.uk_template_param")) {
            errorCode = "DUPLICATE_PARAM_KEY";
            errorMessage = "该模板中已存在相同的参数键，请使用不同的参数键名称";
        } else {
            errorCode = "DUPLICATE_KEY";
            errorMessage = "数据已存在，请检查输入内容";
        }

        return WebAdminResponse.error(errorCode, errorMessage);
    }

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public WebAdminResponse<Object> handleValidationException(Exception e) {
        log.warn("Validation exception: {}", e.getMessage());

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

        return WebAdminResponse.validationError(
            errors.isEmpty() ? "参数验证失败" : errors.toString());
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public WebAdminResponse<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return WebAdminResponse.error("INVALID_ARGUMENT", e.getMessage());
    }

    /**
     * 处理其他未捕获异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public WebAdminResponse<Object> handleException(Exception e) {
        log.error("Unexpected error: ", e);
        return WebAdminResponse.error("INTERNAL_ERROR", "服务器内部错误");
    }
}
