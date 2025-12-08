package com.sunny.admin.config;

import com.sunny.common.exception.GlobalException;
import com.sunny.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * API 模块异常处理器
 * 继承 common 的 GlobalExceptionHandler 基类
 *
 * @author sunny
 * @since 2024-01-01
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends com.sunny.common.handler.GlobalExceptionHandler {

    @Override
    protected Logger getLogger() {
        return log;
    }

    @Override
    @ExceptionHandler(GlobalException.class)
    public ApiResponse<Object> handleGlobalException(GlobalException e) {
        return super.handleGlobalException(e);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ApiResponse<Object> handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("Duplicate key exception occurred: {}", e.getMessage());

        String message = e.getMessage();
        String errorCode;
        String errorMessage;

        // 检查是否是模板参数的唯一约束违反
        if (message != null && message.contains("template_parameters.uk_template_param")) {
            errorCode = "DUPLICATE_PARAM_KEY";
            errorMessage = "该模板中已存在相同的参数键，请使用不同的参数键名称";
        } else {
            errorCode = "DUPLICATE_KEY";
            errorMessage = "数据已存在，请检查输入内容";
        }

        return ApiResponse.error(errorCode, errorMessage);
    }

    @Override
    @ExceptionHandler(Exception.class)
    public ApiResponse<Object> handleException(Exception e) {
        return super.handleException(e);
    }
}