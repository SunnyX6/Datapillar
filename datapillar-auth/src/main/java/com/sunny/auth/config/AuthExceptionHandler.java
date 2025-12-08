package com.sunny.auth.config;

import com.sunny.common.exception.GlobalException;
import com.sunny.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
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
 * 继承 common 的 GlobalExceptionHandler 基类
 *
 * @author sunny
 * @since 2024-11-08
 */
@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler extends com.sunny.common.handler.GlobalExceptionHandler {

    @Override
    protected Logger getLogger() {
        return log;
    }

    @Override
    @ExceptionHandler(GlobalException.class)
    public ApiResponse<Object> handleGlobalException(GlobalException e) {
        return super.handleGlobalException(e);
    }

    /**
     * 处理唯一键冲突异常
     * 如：用户名已存在、邮箱已注册等
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Object> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("Duplicate key exception: {}", e.getMessage());

        String message = e.getMessage();
        String errorMessage;

        // 根据具体的唯一键冲突提供更友好的错误信息
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

        return ApiResponse.error("DUPLICATE_KEY", errorMessage);
    }

    /**
     * 处理参数验证异常 (400)
     * 如：@Valid注解验证失败
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> handleMethodValidationException(Exception e) {
        log.warn("Validation exception: {}", e.getMessage());

        Map<String, String> errors = new HashMap<>();

        if (e instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException ex = (MethodArgumentNotValidException) e;
            ex.getBindingResult().getAllErrors().forEach((error) -> {
                String fieldName = ((FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                errors.put(fieldName, errorMessage);
            });
        } else if (e instanceof BindException) {
            BindException ex = (BindException) e;
            ex.getBindingResult().getAllErrors().forEach((error) -> {
                String fieldName = ((FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                errors.put(fieldName, errorMessage);
            });
        }

        return ApiResponse.validationError(
            errors.isEmpty() ? "参数验证失败" : errors.toString()
        );
    }

    /**
     * 处理非法参数异常 (400)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return ApiResponse.error("INVALID_ARGUMENT", e.getMessage());
    }

    @Override
    @ExceptionHandler(Exception.class)
    public ApiResponse<Object> handleException(Exception e) {
        return super.handleException(e);
    }
}
