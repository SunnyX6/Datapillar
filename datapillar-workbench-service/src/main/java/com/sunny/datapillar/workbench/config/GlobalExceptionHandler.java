package com.sunny.datapillar.workbench.config;

import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.workbench.web.handler.BaseGlobalExceptionHandler;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Workbench Service 异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends BaseGlobalExceptionHandler {

    @Override
    protected ErrorCode getValidationErrorCode() {
        return ErrorCode.ADMIN_VALIDATION_ERROR;
    }

    @Override
    protected ErrorCode getInvalidArgumentErrorCode() {
        return ErrorCode.ADMIN_INVALID_ARGUMENT;
    }

    @Override
    protected ErrorCode getDuplicateKeyErrorCode() {
        return ErrorCode.ADMIN_DUPLICATE_KEY;
    }

    @Override
    protected ErrorCode resolveDuplicateKeyErrorCode(DuplicateKeyException e) {
        String message = e.getMessage();
        if (message != null && message.contains("template_parameters.uk_template_param")) {
            return ErrorCode.ADMIN_DUPLICATE_PARAM_KEY;
        }
        return getDuplicateKeyErrorCode();
    }

    @Override
    protected String resolveDuplicateKeyMessage(DuplicateKeyException e, ErrorCode errorCode) {
        if (errorCode == ErrorCode.ADMIN_DUPLICATE_PARAM_KEY) {
            return "该模板中已存在相同的参数键，请使用不同的参数键名称";
        }
        return "数据已存在，请检查输入内容";
    }
}
