package com.sunny.datapillar.auth.service.login;

/**
 * Enum for supported login methods.
 *
 * @author Sunny
 * @date 2026-01-01
 */
public enum LoginMethodEnum {
  PASSWORD("password"),
  SSO("sso");

  private final String key;

  LoginMethodEnum(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }
}
