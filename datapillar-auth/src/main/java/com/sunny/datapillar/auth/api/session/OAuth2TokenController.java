package com.sunny.datapillar.auth.api.session;

import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.auth.dto.oauth.response.OAuth2TokenResponse;
import com.sunny.datapillar.auth.service.SessionAppService;
import com.sunny.datapillar.auth.token.TokenEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OAuth2 token endpoint controller. */
@RestController
@RequestMapping("/oauth2")
@ConditionalOnProperty(prefix = "auth", name = "authenticator", havingValue = "oauth2")
public class OAuth2TokenController {

  private final SessionAppService sessionAppService;
  private final TokenEngine tokenEngine;
  private final AuthProperties authProperties;

  public OAuth2TokenController(
      SessionAppService sessionAppService, TokenEngine tokenEngine, AuthProperties authProperties) {
    this.sessionAppService = sessionAppService;
    this.tokenEngine = tokenEngine;
    this.authProperties = authProperties;
  }

  @PostMapping("/token")
  public OAuth2TokenResponse token(
      @RequestParam Map<String, String> request,
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse) {
    String grantType = normalize(request.get("grant_type"));
    if ("authorization_code".equals(grantType)) {
      sessionAppService.loginOauth2(
          request.get("provider"),
          request.get("code"),
          request.get("state"),
          request.get("nonce"),
          request.get("code_verifier"),
          request.get("tenant_code"),
          parseBoolean(request.get("remember_me")),
          httpServletRequest,
          httpServletResponse);
      return buildTokenResponse(httpServletResponse);
    }
    if ("refresh_token".equals(grantType)) {
      sessionAppService.refresh(request.get("refresh_token"), httpServletResponse);
      return buildTokenResponse(httpServletResponse);
    }
    throw new com.sunny.datapillar.common.exception.BadRequestException("unsupported_grant_type");
  }

  private OAuth2TokenResponse buildTokenResponse(HttpServletResponse response) {
    String accessToken = extractCookieValue(response.getHeaders("Set-Cookie"), "auth-token");
    String refreshToken = extractCookieValue(response.getHeaders("Set-Cookie"), "refresh-token");
    if (accessToken == null || accessToken.isBlank()) {
      throw new com.sunny.datapillar.common.exception.InternalException(
          "access token issue failed");
    }
    OAuth2TokenResponse tokenResponse = new OAuth2TokenResponse();
    tokenResponse.setAccessToken(accessToken);
    tokenResponse.setRefreshToken(refreshToken);
    tokenResponse.setTokenType("Bearer");
    tokenResponse.setExpiresIn(tokenEngine.accessTokenTtlSeconds());
    tokenResponse.setScope(authProperties.getToken().getAudience());
    return tokenResponse;
  }

  private String extractCookieValue(Collection<String> setCookieHeaders, String cookieName) {
    if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
      return null;
    }
    String prefix = cookieName + "=";
    for (String header : setCookieHeaders) {
      if (header == null || !header.startsWith(prefix)) {
        continue;
      }
      int end = header.indexOf(';');
      if (end < 0) {
        return header.substring(prefix.length());
      }
      return header.substring(prefix.length(), end);
    }
    return null;
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private Boolean parseBoolean(String value) {
    String normalized = normalize(value);
    if (normalized == null) {
      return null;
    }
    return Boolean.parseBoolean(normalized);
  }
}
