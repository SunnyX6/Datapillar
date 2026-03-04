package com.sunny.datapillar.openlineage.exception;

import com.sunny.datapillar.common.exception.ConflictException;

/** OpenLineage Tenant inconsistency exception. */
public class OpenLineageTenantMismatchException extends ConflictException {

  public OpenLineageTenantMismatchException(String message, Object... args) {
    super(message, args);
  }
}
