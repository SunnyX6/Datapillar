package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import java.util.Map;

/**
 * SSO There is an abnormality in the identity Describe user identity binding unique constraint
 * violation
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoIdentityAlreadyExistsException extends AlreadyExistsException {

  public SsoIdentityAlreadyExistsException() {
    super(ErrorType.SSO_IDENTITY_ALREADY_EXISTS, Map.of(), "SSOIdentity already exists");
  }

  public SsoIdentityAlreadyExistsException(Throwable cause) {
    super(cause, ErrorType.SSO_IDENTITY_ALREADY_EXISTS, Map.of(), "SSOIdentity already exists");
  }
}
