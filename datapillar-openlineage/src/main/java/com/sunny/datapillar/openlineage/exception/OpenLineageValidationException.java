package com.sunny.datapillar.openlineage.exception;

import com.sunny.datapillar.common.exception.BadRequestException;

/** OpenLineage Input parameter verification exception. */
public class OpenLineageValidationException extends BadRequestException {

  public OpenLineageValidationException(String message, Object... args) {
    super(message, args);
  }

  public OpenLineageValidationException(Throwable cause, String message, Object... args) {
    super(cause, message, args);
  }
}
