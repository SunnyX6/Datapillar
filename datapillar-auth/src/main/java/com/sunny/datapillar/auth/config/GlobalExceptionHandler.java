package com.sunny.datapillar.auth.config;

import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.auth.web.handler.BaseGlobalExceptionHandler;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证服务异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    @Override
    protected ErrorCode getValidationErrorCode() {
        return ErrorCode.AUTH_VALIDATION_ERROR;
    }

    @Override
    protected ErrorCode getInvalidArgumentErrorCode() {
        return ErrorCode.AUTH_INVALID_ARGUMENT;
    }

    @Override
    protected ErrorCode getDuplicateKeyErrorCode() {
        return ErrorCode.AUTH_DUPLICATE_KEY;
    }

    @Override
    protected String resolveDuplicateKeyMessage(DuplicateKeyException e, ErrorCode errorCode) {
        String message = e.getMessage();
        if (message == null) {
            return "数据已存在";
        }
        if (message.contains("username")) {
            return "用户名已存在";
        }
        if (message.contains("email")) {
            return "邮箱已被注册";
        }
        return "数据已存在，请检查输入内容";
    }
}
