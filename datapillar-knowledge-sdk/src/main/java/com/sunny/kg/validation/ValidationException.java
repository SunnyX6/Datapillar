package com.sunny.kg.validation;

import com.sunny.kg.exception.KnowledgeErrorCode;
import com.sunny.kg.exception.KnowledgeException;

/**
 * 数据校验异常
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class ValidationException extends KnowledgeException {

    public ValidationException(String field, String message) {
        super(KnowledgeErrorCode.VALIDATION_ERROR, field, message);
    }

    public ValidationException(String message) {
        super(KnowledgeErrorCode.VALIDATION_ERROR_SIMPLE, message);
    }

}
