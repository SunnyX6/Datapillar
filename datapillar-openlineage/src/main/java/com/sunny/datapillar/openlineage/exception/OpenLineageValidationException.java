package com.sunny.datapillar.openlineage.exception;

import com.sunny.datapillar.common.exception.BadRequestException;

/**
 * OpenLineage 入参校验异常。
 */
public class OpenLineageValidationException extends BadRequestException {

    public OpenLineageValidationException(String message, Object... args) {
        super(message, args);
    }

    public OpenLineageValidationException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }
}
