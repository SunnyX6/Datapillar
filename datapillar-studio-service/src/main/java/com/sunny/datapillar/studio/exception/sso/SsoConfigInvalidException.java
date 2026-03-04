package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.InternalException;
import java.util.Map;

/**
 * SSO Invalid configuration exception Describes illegal semantics of server-side persistence
 * configuration content
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoConfigInvalidException extends InternalException {

  public SsoConfigInvalidException() {
    super(ErrorType.SSO_CONFIG_INVALID, Map.of(), "SSOInvalid configuration");
  }

  public SsoConfigInvalidException(Throwable cause) {
    super(cause, ErrorType.SSO_CONFIG_INVALID, Map.of(), "SSOInvalid configuration");
  }
}
