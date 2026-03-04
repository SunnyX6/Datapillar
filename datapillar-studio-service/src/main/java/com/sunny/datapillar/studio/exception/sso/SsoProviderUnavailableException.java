package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import java.util.Map;

/**
 * SSO Supplier unavailable exception Describe external SSO Supplier is temporarily unavailable
 * semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoProviderUnavailableException extends ServiceUnavailableException {

  public SsoProviderUnavailableException() {
    super(ErrorType.SSO_PROVIDER_UNAVAILABLE, Map.of(), "SSOService is temporarily unavailable");
  }

  public SsoProviderUnavailableException(Throwable cause) {
    super(
        cause,
        ErrorType.SSO_PROVIDER_UNAVAILABLE,
        Map.of(),
        "SSOService is temporarily unavailable");
  }
}
