package com.sunny.datapillar.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

  @Test
  void validate_shouldIgnoreOauth2SettingsWhenAuthenticatorIsSimple() {
    AuthProperties properties = new AuthProperties();
    properties.setAuthenticator(AuthProperties.AuthenticatorType.SIMPLE);
    properties.setOauth2(null);

    assertDoesNotThrow(properties::validate);
  }

  @Test
  void validate_shouldRequireOauth2SettingsWhenAuthenticatorIsOauth2() {
    AuthProperties properties = new AuthProperties();
    properties.setAuthenticator(AuthProperties.AuthenticatorType.OAUTH2);
    properties.setOauth2(null);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, properties::validate);

    assertEquals(
        "auth.oauth2 must be configured when auth.authenticator=oauth2", exception.getMessage());
  }

  @Test
  void validate_shouldRejectBlankOauth2ProviderWhenOauth2AuthenticatorIsEnabled() {
    AuthProperties properties = new AuthProperties();
    properties.setAuthenticator(AuthProperties.AuthenticatorType.OAUTH2);
    properties.getOauth2().setProvider(" ");

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, properties::validate);

    assertEquals("auth.oauth2.provider cannot be empty", exception.getMessage());
  }
}
