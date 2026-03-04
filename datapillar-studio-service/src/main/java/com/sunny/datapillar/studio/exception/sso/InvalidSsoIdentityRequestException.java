package com.sunny.datapillar.studio.exception.sso;

import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.Map;

/**
 * SSO Identity request parameter invalid exception Description SSO Identity binding request
 * parameters have illegal semantics
 *
 * @author Sunny
 * @date 2026-02-26
 */
public class InvalidSsoIdentityRequestException extends BadRequestException {

  public InvalidSsoIdentityRequestException() {
    super(ErrorType.SSO_IDENTITY_REQUEST_INVALID, Map.of(), "SSOIdentity parameter error");
  }

  public InvalidSsoIdentityRequestException(Throwable cause) {
    super(cause, ErrorType.SSO_IDENTITY_REQUEST_INVALID, Map.of(), "SSOIdentity parameter error");
  }
}
