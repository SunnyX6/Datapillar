package com.sunny.datapillar.auth.authentication;

import com.sunny.datapillar.auth.dto.login.response.SsoQrResponse;

/** Authenticator SPI. */
public interface Authenticator {

  String name();

  AuthenticationResult authenticate(AuthenticationRequest request);

  default SsoQrResponse authorize(AuthenticationRequest request) {
    throw new com.sunny.datapillar.common.exception.BadRequestException(
        "auth.authenticator=%s does not support authorize", name());
  }
}
