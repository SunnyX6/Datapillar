package com.sunny.datapillar.auth.authentication.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.auth.authentication.AuthenticationRequest;
import com.sunny.datapillar.auth.authentication.AuthenticationResult;
import com.sunny.datapillar.auth.dto.login.response.SsoQrResponse;
import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.service.login.LoginSubject;
import com.sunny.datapillar.auth.service.login.method.SsoLoginMethod;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticatorTest {

  @Mock private SsoLoginMethod ssoLoginMethod;
  @Mock private TenantMapper tenantMapper;

  @InjectMocks private OAuth2Authenticator oauth2Authenticator;

  @Test
  void authenticate_shouldMapRequest() {
    LoginSubject subject = LoginSubject.builder().loginMethod("sso").build();
    when(ssoLoginMethod.authenticate(any())).thenReturn(subject);

    AuthenticationRequest request =
        AuthenticationRequest.builder()
            .provider("dingtalk")
            .code("code-1")
            .state("state-1")
            .nonce("nonce-1")
            .codeVerifier("code-verifier")
            .tenantCode("tenant-a")
            .rememberMe(true)
            .clientIp("127.0.0.1")
            .build();

    AuthenticationResult result = oauth2Authenticator.authenticate(request);

    assertEquals("oauth2", result.getAuthenticator());
    assertEquals(subject, result.getSubject());

    ArgumentCaptor<com.sunny.datapillar.auth.service.login.LoginCommand> captor =
        ArgumentCaptor.forClass(com.sunny.datapillar.auth.service.login.LoginCommand.class);
    verify(ssoLoginMethod).authenticate(captor.capture());
    assertEquals("dingtalk", captor.getValue().getProvider());
    assertEquals("nonce-1", captor.getValue().getNonce());
    assertEquals("code-verifier", captor.getValue().getCodeVerifier());
  }

  @Test
  void authorize_shouldBuildSsoQr() {
    Tenant tenant = new Tenant();
    tenant.setId(10L);
    tenant.setCode("tenant-a");
    tenant.setStatus(1);
    when(tenantMapper.selectByCode("tenant-a")).thenReturn(tenant);

    SsoQrResponse qrResponse = new SsoQrResponse("SDK", "state-1", Map.of("state", "state-1"));
    when(ssoLoginMethod.buildQr(10L, "dingtalk", "nonce-1", "challenge-1", "S256"))
        .thenReturn(qrResponse);

    AuthenticationRequest request =
        AuthenticationRequest.builder()
            .provider("dingtalk")
            .tenantCode("tenant-a")
            .nonce("nonce-1")
            .codeChallenge("challenge-1")
            .build();

    SsoQrResponse result = oauth2Authenticator.authorize(request);

    assertEquals("state-1", result.getState());
    verify(ssoLoginMethod).buildQr(10L, "dingtalk", "nonce-1", "challenge-1", "S256");
  }

  @Test
  void authorize_shouldRejectMissingTenant() {
    when(tenantMapper.selectByCode("tenant-a")).thenReturn(null);

    AuthenticationRequest request =
        AuthenticationRequest.builder()
            .provider("dingtalk")
            .tenantCode("tenant-a")
            .nonce("nonce-1")
            .codeChallenge("challenge-1")
            .build();

    com.sunny.datapillar.common.exception.UnauthorizedException exception =
        assertThrows(
            com.sunny.datapillar.common.exception.UnauthorizedException.class,
            () -> oauth2Authenticator.authorize(request));

    assertEquals("Tenant not found: tenant-a", exception.getMessage());
  }
}
