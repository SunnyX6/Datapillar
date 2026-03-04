package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ForbiddenException;
import java.util.Map;

/**
 * SSO Configuration disabled exception Describe the tenant SSO Configure unavailable semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoConfigDisabledException extends ForbiddenException {

  public SsoConfigDisabledException() {
    super(ErrorType.SSO_CONFIG_DISABLED, Map.of(), "SSOConfiguration is disabled");
  }

  public SsoConfigDisabledException(Throwable cause) {
    super(cause, ErrorType.SSO_CONFIG_DISABLED, Map.of(), "SSOConfiguration is disabled");
  }
}
