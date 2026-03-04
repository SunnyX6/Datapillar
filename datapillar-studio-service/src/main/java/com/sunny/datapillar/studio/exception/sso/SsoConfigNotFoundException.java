package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.NotFoundException;
import java.util.Map;

/**
 * SSO There is no exception in the configuration Description Tenant-specified vendor configuration
 * does not have semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoConfigNotFoundException extends NotFoundException {

  public SsoConfigNotFoundException() {
    super(ErrorType.SSO_CONFIG_NOT_FOUND, Map.of(), "SSOConfiguration does not exist");
  }

  public SsoConfigNotFoundException(Throwable cause) {
    super(cause, ErrorType.SSO_CONFIG_NOT_FOUND, Map.of(), "SSOConfiguration does not exist");
  }
}
