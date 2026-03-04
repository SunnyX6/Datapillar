package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * SSO There is an exception in the configuration Describe the tenant SSO Configure unique
 * constraint conflicts
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoConfigAlreadyExistsException extends AlreadyExistsException {

  public SsoConfigAlreadyExistsException() {
    super(ErrorType.SSO_CONFIG_ALREADY_EXISTS, Map.of(), "SSOConfiguration already exists");
  }

  public SsoConfigAlreadyExistsException(Throwable cause) {
    super(cause, ErrorType.SSO_CONFIG_ALREADY_EXISTS, Map.of(), "SSOConfiguration already exists");
  }
}
