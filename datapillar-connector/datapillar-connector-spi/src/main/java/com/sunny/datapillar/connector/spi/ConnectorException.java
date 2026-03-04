package com.sunny.datapillar.connector.spi;

import java.util.Map;

/** Unified connector exception carrying platform error type. */
public class ConnectorException extends RuntimeException {

  private final String errorType;
  private final Map<String, String> context;

  public ConnectorException(String errorType, String message) {
    super(message);
    this.errorType = errorType;
    this.context = Map.of();
  }

  public ConnectorException(String errorType, String message, Throwable cause) {
    super(message, cause);
    this.errorType = errorType;
    this.context = Map.of();
  }

  public ConnectorException(
      String errorType, String message, Map<String, String> context, Throwable cause) {
    super(message, cause);
    this.errorType = errorType;
    this.context = context == null ? Map.of() : Map.copyOf(context);
  }

  public String errorType() {
    return errorType;
  }

  public Map<String, String> context() {
    return context;
  }
}
