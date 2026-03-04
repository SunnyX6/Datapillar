package com.sunny.datapillar.gateway.exception.base;

import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * GatewayBadRequestExceptionSemantic anomalies Default concrete implementation of abstract semantic
 * exceptions
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class GatewayBadRequestException extends BadRequestException {

  public GatewayBadRequestException(String message, Object... args) {
    super(message, args);
  }

  public GatewayBadRequestException(Throwable cause, String message, Object... args) {
    super(cause, message, args);
  }

  public GatewayBadRequestException(
      String type, Map<String, String> context, String message, Object... args) {
    super(type, context, message, args);
  }

  public GatewayBadRequestException(
      Throwable cause, String type, Map<String, String> context, String message, Object... args) {
    super(cause, type, context, message, args);
  }
}
