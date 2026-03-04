package com.sunny.datapillar.auth.exception.login;

import com.sunny.datapillar.common.exception.BadRequestException;

/**
 * Exception for unsupported login methods.
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class LoginMethodNotSupportedException extends BadRequestException {

  public LoginMethodNotSupportedException(String requestedMethod) {
    super("Unsupported login method: %s", requestedMethod == null ? "null" : requestedMethod);
  }
}
