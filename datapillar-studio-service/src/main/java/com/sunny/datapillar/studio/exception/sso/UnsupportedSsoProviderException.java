package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * Not supported SSO Supplier exception Description The current tenant requested an unsupported SSO
 * supplier semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class UnsupportedSsoProviderException extends BadRequestException {

  public UnsupportedSsoProviderException() {
    super(ErrorType.SSO_PROVIDER_UNSUPPORTED, Map.of(), "Not supportedSSOsupplier");
  }

  public UnsupportedSsoProviderException(Throwable cause) {
    super(cause, ErrorType.SSO_PROVIDER_UNSUPPORTED, Map.of(), "Not supportedSSOsupplier");
  }
}
