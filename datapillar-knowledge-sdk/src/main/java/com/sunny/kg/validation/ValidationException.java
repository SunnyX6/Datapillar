package com.sunny.kg.validation;

import com.sunny.kg.exception.KnowledgeException;

/**
 * 数据校验异常
 *
 * @author Sunny
 * @since 2025-12-11
 */
public class ValidationException extends KnowledgeException {

    public ValidationException(String field, String message) {
        super("VALIDATION_ERROR", String.format("字段 [%s] 校验失败: %s", field, message));
    }

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

}
