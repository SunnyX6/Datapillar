package com.sunny.datapillar.auth.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.sunny.datapillar.auth.api.session.OAuth2TokenController;
import com.sunny.datapillar.auth.api.wellknown.WellKnownController;
import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.auth.dto.oauth.response.OAuth2TokenResponse;
import com.sunny.datapillar.auth.service.SessionAppService;
import com.sunny.datapillar.auth.service.TokenAppService;
import com.sunny.datapillar.auth.token.TokenEngine;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class MetadataAndTokenEndpointTest {

  @Mock private TokenAppService tokenAppService;
  @Mock private SessionAppService sessionAppService;
  @Mock private TokenEngine tokenEngine;

  private AuthProperties authProperties;

  @BeforeEach
  void setUp() {
    authProperties = new AuthProperties();
    authProperties.getToken().setAudience("datapillar-api");
  }

  @Test
  void shouldReturnJwksAndOpenidConfiguration() {
    Map<String, Object> jwks =
        Map.of(
            "keys",
            List.of(Map.of("kty", "OKP", "crv", "Ed25519", "kid", "auth-dev-2026-01", "x", "abc")));
    Map<String, Object> discovery = new LinkedHashMap<>();
    discovery.put("issuer", "https://auth.datapillar.local");
    discovery.put("jwks_uri", "https://auth.datapillar.local/.well-known/jwks.json");
    discovery.put("token_endpoint", "https://auth.datapillar.local/oauth2/token");

    when(tokenAppService.jwks()).thenReturn(jwks);
    when(tokenAppService.openidConfiguration()).thenReturn(discovery);

    WellKnownController controller = new WellKnownController(tokenAppService);

    assertEquals(
        "auth-dev-2026-01",
        ((Map<?, ?>) ((List<?>) controller.jwks().get("keys")).get(0)).get("kid"));
    assertEquals(
        "https://auth.datapillar.local/oauth2/token",
        controller.openidConfiguration().get("token_endpoint"));
  }

  @Test
  void shouldSupportAuthorizationCodeGrant() {
    when(tokenEngine.accessTokenTtlSeconds()).thenReturn(3600L);
    doAnswer(
            invocation -> {
              HttpServletResponse response = invocation.getArgument(8, HttpServletResponse.class);
              response.addHeader("Set-Cookie", "auth-token=access-token-value; Path=/; HttpOnly");
              response.addHeader(
                  "Set-Cookie", "refresh-token=refresh-token-value; Path=/; HttpOnly");
              return null;
            })
        .when(sessionAppService)
        .loginOauth2(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            any());

    OAuth2TokenController controller =
        new OAuth2TokenController(sessionAppService, tokenEngine, authProperties);

    Map<String, String> request = new LinkedHashMap<>();
    request.put("grant_type", "authorization_code");
    request.put("provider", "dingtalk");
    request.put("code", "code-1");
    request.put("state", "state-1");
    request.put("nonce", "nonce-1");
    request.put("code_verifier", "verifier-1");
    request.put("tenant_code", "tenant-a");

    OAuth2TokenResponse response =
        controller.token(request, new MockHttpServletRequest(), new MockHttpServletResponse());

    assertEquals("access-token-value", response.getAccessToken());
    assertEquals("refresh-token-value", response.getRefreshToken());
    assertEquals("Bearer", response.getTokenType());
  }

  @Test
  void shouldSupportRefreshTokenGrant() {
    when(tokenEngine.accessTokenTtlSeconds()).thenReturn(3600L);
    doAnswer(
            invocation -> {
              HttpServletResponse response = invocation.getArgument(1, HttpServletResponse.class);
              response.addHeader("Set-Cookie", "auth-token=access-token-refresh; Path=/; HttpOnly");
              response.addHeader(
                  "Set-Cookie", "refresh-token=refresh-token-refresh; Path=/; HttpOnly");
              return null;
            })
        .when(sessionAppService)
        .refresh(anyString(), any(HttpServletResponse.class));

    OAuth2TokenController controller =
        new OAuth2TokenController(sessionAppService, tokenEngine, authProperties);

    OAuth2TokenResponse response =
        controller.token(
            Map.of("grant_type", "refresh_token", "refresh_token", "refresh-token-old"),
            new MockHttpServletRequest(),
            new MockHttpServletResponse());

    assertEquals("access-token-refresh", response.getAccessToken());
    assertEquals("refresh-token-refresh", response.getRefreshToken());
  }

  @Test
  void shouldRejectUnsupportedGrantType() {
    OAuth2TokenController controller =
        new OAuth2TokenController(sessionAppService, tokenEngine, authProperties);

    assertThrows(
        com.sunny.datapillar.common.exception.BadRequestException.class,
        () ->
            controller.token(
                Map.of("grant_type", "client_credentials"),
                new MockHttpServletRequest(),
                new MockHttpServletResponse()));
  }
}
