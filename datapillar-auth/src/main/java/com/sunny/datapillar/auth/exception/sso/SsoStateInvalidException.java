package com.sunny.datapillar.auth.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import java.util.Map;

/**
 * Exception for invalid or expired SSO state.
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoStateInvalidException extends UnauthorizedException {

  public SsoStateInvalidException() {
    super(ErrorType.SSO_STATE_INVALID, Map.of(), "Invalid SSO state");
  }

  public SsoStateInvalidException(Throwable cause) {
    super(cause, ErrorType.SSO_STATE_INVALID, Map.of(), "Invalid SSO state");
  }
}
