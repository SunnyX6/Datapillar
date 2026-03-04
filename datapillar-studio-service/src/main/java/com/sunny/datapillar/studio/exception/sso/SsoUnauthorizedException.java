package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import java.util.Map;

/**
 * SSO Unauthorized exception Describe unauthorized semantics caused by missing tenant context
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoUnauthorizedException extends UnauthorizedException {

  public SsoUnauthorizedException() {
    super(ErrorType.SSO_UNAUTHORIZED, Map.of(), "Unauthorized access");
  }

  public SsoUnauthorizedException(Throwable cause) {
    super(cause, ErrorType.SSO_UNAUTHORIZED, Map.of(), "Unauthorized access");
  }
}
