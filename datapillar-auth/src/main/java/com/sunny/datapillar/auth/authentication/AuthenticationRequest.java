package com.sunny.datapillar.auth.authentication;

import lombok.Builder;
import lombok.Data;

/** Authentication request model for simple/oauth2 authenticator. */
@Data
@Builder
public class AuthenticationRequest {

  private String loginAlias;
  private String password;
  private String tenantCode;
  private Boolean rememberMe;
  private String clientIp;

  private String provider;
  private String code;
  private String state;
  private String nonce;
  private String codeVerifier;
  private String codeChallenge;
  private String codeChallengeMethod;
}
