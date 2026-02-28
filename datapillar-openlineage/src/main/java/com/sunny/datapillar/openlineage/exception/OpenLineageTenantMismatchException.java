package com.sunny.datapillar.openlineage.exception;

import com.sunny.datapillar.common.exception.ConflictException;

/**
 * OpenLineage 租户不一致异常。
 */
public class OpenLineageTenantMismatchException extends ConflictException {

    public OpenLineageTenantMismatchException(String message, Object... args) {
        super(message, args);
    }
}
