package com.sunny.datapillar.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.sunny.datapillar.auth.authentication.Authenticator;
import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.auth.config.AuthSecurityProperties;
import com.sunny.datapillar.auth.service.login.LoginFinalizer;
import org.junit.jupiter.api.Test;

class SessionAppServiceTest {

  private SessionAppService createService() {
    return new SessionAppService(
        mock(Authenticator.class),
        new AuthProperties(),
        mock(LoginFinalizer.class),
        mock(LoginService.class),
        mock(AuthService.class),
        new AuthSecurityProperties());
  }

  @Test
  void extractAccessToken_shouldRejectMultipleCredentials() {
    SessionAppService service = createService();

    com.sunny.datapillar.common.exception.UnauthorizedException exception =
        assertThrows(
            com.sunny.datapillar.common.exception.UnauthorizedException.class,
            () -> service.extractAccessToken("Bearer header-token", "cookie-token"));

    assertEquals("Multiple authentication credentials are not allowed", exception.getMessage());
  }

  @Test
  void extractAccessToken_shouldRejectInvalidAuthorizationHeader() {
    SessionAppService service = createService();

    com.sunny.datapillar.common.exception.UnauthorizedException exception =
        assertThrows(
            com.sunny.datapillar.common.exception.UnauthorizedException.class,
            () -> service.extractAccessToken("Basic abc", null));

    assertEquals("Invalid Authorization header", exception.getMessage());
  }

  @Test
  void extractAccessToken_shouldPreferSingleBearerToken() {
    SessionAppService service = createService();

    assertEquals("header-token", service.extractAccessToken("Bearer header-token", null));
  }
}
