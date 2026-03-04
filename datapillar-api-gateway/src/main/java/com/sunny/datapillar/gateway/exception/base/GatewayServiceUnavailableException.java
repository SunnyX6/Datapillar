package com.sunny.datapillar.gateway.exception.base;

import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import java.util.Map;

/**
 * GatewayServiceUnavailableExceptionSemantic anomalies Default concrete implementation of abstract
 * semantic exceptions
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class GatewayServiceUnavailableException extends ServiceUnavailableException {

  public GatewayServiceUnavailableException(String message, Object... args) {
    super(message, args);
  }

  public GatewayServiceUnavailableException(Throwable cause, String message, Object... args) {
    super(cause, message, args);
  }

  public GatewayServiceUnavailableException(
      String type, Map<String, String> context, String message, Object... args) {
    super(type, context, message, args);
  }

  public GatewayServiceUnavailableException(
      Throwable cause, String type, Map<String, String> context, String message, Object... args) {
    super(cause, type, context, message, args);
  }
}
