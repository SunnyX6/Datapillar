package com.sunny.datapillar.auth.config;

import com.sunny.datapillar.auth.response.AuthException;
import com.sunny.datapillar.auth.response.AuthResponse;
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
 * 认证服务异常处理器
 */
@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler {

    /**
     * 处理认证异常
     */
    @ExceptionHandler(AuthException.class)
    public AuthResponse<Object> handleAuthException(AuthException e) {
        log.warn("Auth exception: code={}, message={}", e.getCode(), e.getMessage());
        return AuthResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理唯一键冲突异常
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public AuthResponse<Object> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("Duplicate key exception: {}", e.getMessage());

        String message = e.getMessage();
        String errorMessage;

        if (message != null) {
            if (message.contains("username")) {
                errorMessage = "用户名已存在";
            } else if (message.contains("email")) {
                errorMessage = "邮箱已被注册";
            } else {
                errorMessage = "数据已存在，请检查输入内容";
            }
        } else {
            errorMessage = "数据已存在";
        }

        return AuthResponse.error("DUPLICATE_KEY", errorMessage);
    }

    /**
     * 处理参数验证异常
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AuthResponse<Object> handleValidationException(Exception e) {
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

        return AuthResponse.error("VALIDATION_ERROR",
            errors.isEmpty() ? "参数验证失败" : errors.toString());
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public AuthResponse<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return AuthResponse.error("INVALID_ARGUMENT", e.getMessage());
    }

    /**
     * 处理其他未捕获异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public AuthResponse<Object> handleException(Exception e) {
        log.error("Unexpected error: ", e);
        return AuthResponse.error("INTERNAL_ERROR", "服务器内部错误");
    }
}
