package com.sunny.datapillar.gateway.exception.base;

import com.sunny.datapillar.common.exception.ForbiddenException;
import java.util.Map;

/**
 * GatewayForbiddenExceptionSemantic anomalies Default concrete implementation of abstract semantic
 * exceptions
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class GatewayForbiddenException extends ForbiddenException {

  public GatewayForbiddenException(String message, Object... args) {
    super(message, args);
  }

  public GatewayForbiddenException(Throwable cause, String message, Object... args) {
    super(cause, message, args);
  }

  public GatewayForbiddenException(
      String type, Map<String, String> context, String message, Object... args) {
    super(type, context, message, args);
  }

  public GatewayForbiddenException(
      Throwable cause, String type, Map<String, String> context, String message, Object... args) {
    super(cause, type, context, message, args);
  }
}
