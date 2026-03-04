package com.sunny.datapillar.auth.authentication;

import com.sunny.datapillar.auth.service.login.LoginSubject;
import lombok.Builder;
import lombok.Data;

/** Authentication result model. */
@Data
@Builder
public class AuthenticationResult {

  private String authenticator;
  private LoginSubject subject;
  private Boolean rememberMe;
}
