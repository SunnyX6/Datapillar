package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.ForbiddenException;
import java.util.Map;

/**
 * SSO Identity access denied exception Description: The current user does not have identity binding
 * permission semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class SsoIdentityAccessDeniedException extends ForbiddenException {

  public SsoIdentityAccessDeniedException() {
    super(ErrorType.SSO_IDENTITY_ACCESS_DENIED, Map.of(), "NoneSSOBind permissions");
  }

  public SsoIdentityAccessDeniedException(Throwable cause) {
    super(cause, ErrorType.SSO_IDENTITY_ACCESS_DENIED, Map.of(), "NoneSSOBind permissions");
  }
}
