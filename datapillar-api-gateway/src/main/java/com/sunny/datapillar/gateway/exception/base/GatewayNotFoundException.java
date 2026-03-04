package com.sunny.datapillar.gateway.exception.base;

import com.sunny.datapillar.common.exception.NotFoundException;
import java.util.Map;

/**
 * GatewayNotFoundExceptionSemantic anomalies Default concrete implementation of abstract semantic
 * exceptions
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class GatewayNotFoundException extends NotFoundException {

  public GatewayNotFoundException(String message, Object... args) {
    super(message, args);
  }

  public GatewayNotFoundException(Throwable cause, String message, Object... args) {
    super(cause, message, args);
  }

  public GatewayNotFoundException(
      String type, Map<String, String> context, String message, Object... args) {
    super(type, context, message, args);
  }

  public GatewayNotFoundException(
      Throwable cause, String type, Map<String, String> context, String message, Object... args) {
    super(cause, type, context, message, args);
  }
}
