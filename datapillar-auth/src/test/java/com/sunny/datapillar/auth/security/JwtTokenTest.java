package com.sunny.datapillar.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sunny.datapillar.auth.config.AuthProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

class JwtTokenTest {

  private JwtToken buildJwtToken() {
    AuthProperties properties = new AuthProperties();
    properties.getToken().setIssuer("https://auth.datapillar.local");
    properties.getToken().setAudience("datapillar-api");
    properties.getToken().setPrivateKeyPath("classpath:security/auth-token-dev-private.pem");
    properties.getToken().setPublicKeyPath("classpath:security/auth-token-dev-public.pem");
    properties.getToken().setAccessTtlSeconds(60);
    properties.getToken().setRefreshTtlSeconds(604800);
    properties.getToken().setRefreshRememberTtlSeconds(2592000);
    properties.getToken().setLoginTtlSeconds(300);
    properties.getJwks().setActiveKid("auth-dev-2026-01");
    properties.validate();
    return new JwtToken(properties);
  }

  @Test
  void generateRefreshToken_shouldUseDefaultExpirationWhenRememberMeFalse() {
    JwtToken jwtToken = buildJwtToken();
    String token = jwtToken.generateRefreshToken(1L, 10L, false);

    Claims claims = jwtToken.parseToken(token);
    long ttlSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;

    assertEquals(jwtToken.getRefreshTokenExpiration(false), ttlSeconds);
    assertNotNull(jwtToken.getSessionId(claims));
    assertNotNull(jwtToken.getTokenId(claims));
  }

  @Test
  void generateRefreshToken_shouldUseRememberExpirationWhenRememberMeTrue() {
    JwtToken jwtToken = buildJwtToken();
    String token = jwtToken.generateRefreshToken(1L, 10L, true);

    Claims claims = jwtToken.parseToken(token);
    long ttlSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;

    assertEquals(jwtToken.getRefreshTokenExpiration(true), ttlSeconds);
    assertNotNull(jwtToken.getSessionId(claims));
    assertNotNull(jwtToken.getTokenId(claims));
  }
}
