package com.sunny.datapillar.auth.authentication.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.auth.authentication.AuthenticationRequest;
import com.sunny.datapillar.auth.authentication.AuthenticationResult;
import com.sunny.datapillar.auth.service.login.LoginSubject;
import com.sunny.datapillar.auth.service.login.method.PasswordLoginMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimpleAuthenticatorTest {

  @Mock private PasswordLoginMethod passwordLoginMethod;

  @InjectMocks private SimpleAuthenticator simpleAuthenticator;

  @Test
  void authenticate_shouldMapRequestAndReturnResult() {
    LoginSubject subject = LoginSubject.builder().loginMethod("password").build();
    when(passwordLoginMethod.authenticate(any())).thenReturn(subject);

    AuthenticationRequest request =
        AuthenticationRequest.builder()
            .loginAlias("sunny")
            .password("123456")
            .tenantCode("tenant-a")
            .rememberMe(true)
            .clientIp("127.0.0.1")
            .build();

    AuthenticationResult result = simpleAuthenticator.authenticate(request);

    assertEquals("simple", result.getAuthenticator());
    assertEquals(subject, result.getSubject());
    assertEquals(true, result.getRememberMe());

    ArgumentCaptor<com.sunny.datapillar.auth.service.login.LoginCommand> captor =
        ArgumentCaptor.forClass(com.sunny.datapillar.auth.service.login.LoginCommand.class);
    verify(passwordLoginMethod).authenticate(captor.capture());
    assertEquals("password", captor.getValue().getMethod());
    assertEquals("sunny", captor.getValue().getLoginAlias());
    assertEquals("tenant-a", captor.getValue().getTenantCode());
  }

  @Test
  void authenticate_shouldPropagateFailure() {
    when(passwordLoginMethod.authenticate(any()))
        .thenThrow(
            new com.sunny.datapillar.common.exception.UnauthorizedException(
                "Invalid username or password"));

    AuthenticationRequest request =
        AuthenticationRequest.builder().loginAlias("sunny").password("wrong").build();

    com.sunny.datapillar.common.exception.UnauthorizedException ex =
        assertThrows(
            com.sunny.datapillar.common.exception.UnauthorizedException.class,
            () -> simpleAuthenticator.authenticate(request));

    assertEquals("Invalid username or password", ex.getMessage());
  }
}
