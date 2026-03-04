package com.sunny.datapillar.openlineage.exception;

import com.sunny.datapillar.common.exception.InternalException;

/** OpenLineage write exception. */
public class OpenLineageWriteException extends InternalException {

  public OpenLineageWriteException(String message, Object... args) {
    super(message, args);
  }

  public OpenLineageWriteException(Throwable cause, String message, Object... args) {
    super(cause, message, args);
  }
}
