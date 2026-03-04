package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.NotFoundException;
import java.util.Map;

/**
 * SSO There is no abnormality in the identity Description: There is no semantics for identity
 * binding records under the tenant.
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoIdentityNotFoundException extends NotFoundException {

  public SsoIdentityNotFoundException() {
    super(ErrorType.SSO_IDENTITY_NOT_FOUND, Map.of(), "SSOIdentity does not exist");
  }

  public SsoIdentityNotFoundException(Throwable cause) {
    super(cause, ErrorType.SSO_IDENTITY_NOT_FOUND, Map.of(), "SSOIdentity does not exist");
  }
}
