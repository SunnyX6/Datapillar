package com.sunny.datapillar.studio.config;

import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.studio.web.handler.BaseGlobalExceptionHandler;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Studio Service 异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    @Override
    protected ErrorCode getValidationErrorCode() {
        return ErrorCode.VALIDATION_ERROR;
    }

    @Override
    protected ErrorCode getInvalidArgumentErrorCode() {
        return ErrorCode.INVALID_ARGUMENT;
    }

    @Override
    protected ErrorCode getDuplicateKeyErrorCode() {
        return ErrorCode.DUPLICATE_KEY;
    }

    @Override
    protected ErrorCode resolveDuplicateKeyErrorCode(DuplicateKeyException e) {
        String message = e.getMessage();
        if (message != null && message.contains("template_parameters.uk_template_param")) {
            return ErrorCode.DUPLICATE_PARAM_KEY;
        }
        return getDuplicateKeyErrorCode();
    }

    @Override
    protected String resolveDuplicateKeyMessage(DuplicateKeyException e, ErrorCode errorCode) {
        if (errorCode == ErrorCode.DUPLICATE_PARAM_KEY) {
            return "该模板中已存在相同的参数键，请使用不同的参数键名称";
        }
        return "数据已存在，请检查输入内容";
    }
}
