package com.sunny.datapillar.openlineage.exception;

import com.sunny.datapillar.common.exception.InternalException;

/**
 * OpenLineage 写入异常。
 */
public class OpenLineageWriteException extends InternalException {

    public OpenLineageWriteException(String message, Object... args) {
        super(message, args);
    }

    public OpenLineageWriteException(Throwable cause, String message, Object... args) {
        super(cause, message, args);
    }
}
