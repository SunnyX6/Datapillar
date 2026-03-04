package com.sunny.datapillar.gateway.exception.base;

import com.sunny.datapillar.common.exception.InternalException;
import java.util.Map;

/**
 * GatewayInternalExceptionSemantic anomalies Default concrete implementation of abstract semantic
 * exceptions
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class GatewayInternalException extends InternalException {

  public GatewayInternalException(String message, Object... args) {
    super(message, args);
  }

  public GatewayInternalException(Throwable cause, String message, Object... args) {
    super(cause, message, args);
  }

  public GatewayInternalException(
      String type, Map<String, String> context, String message, Object... args) {
    super(type, context, message, args);
  }

  public GatewayInternalException(
      Throwable cause, String type, Map<String, String> context, String message, Object... args) {
    super(cause, type, context, message, args);
  }
}
