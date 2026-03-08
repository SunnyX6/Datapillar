package com.sunny.datapillar.auth.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.auth.key.KeySetKeyManager;
import com.sunny.datapillar.auth.security.JwtToken;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtTokenEngineTest {

  private JwtTokenEngine buildEngine(long accessTtlSeconds) {
    AuthProperties properties = new AuthProperties();
    properties.getToken().setIssuer("https://auth.datapillar.local");
    properties.getToken().setAudience("datapillar-api");
    properties.getToken().setKeysetPath("classpath:security/auth-token-dev-keyset.json");
    properties.getToken().setAccessTtlSeconds(accessTtlSeconds);
    properties.getToken().setRefreshTtlSeconds(120);
    properties.getToken().setRefreshRememberTtlSeconds(240);
    properties.getToken().setLoginTtlSeconds(60);
    properties.validate();

    JwtToken jwtToken =
        new JwtToken(properties, new KeySetKeyManager(new ObjectMapper(), properties));
    return new JwtTokenEngine(jwtToken, new ClaimAssembler());
  }

  @Test
  void issueAndVerify_shouldPass() {
    JwtTokenEngine engine = buildEngine(3600);
    TokenClaims claims =
        TokenClaims.builder()
            .userId(101L)
            .tenantId(1001L)
            .tenantCode("tenant-a")
            .tenantCodes(List.of("tenant-a"))
            .preferredUsername("sunny")
            .email("sunny@datapillar.ai")
            .roles(List.of("ADMIN"))
            .sessionId("sid-1")
            .tokenId("jti-1")
            .tokenType("access")
            .build();

    String token = engine.issueAccessToken(claims);
    Claims parsed = engine.verify(token);

    assertEquals("access", parsed.get("token_type", String.class));
    assertEquals(101L, ((Number) parsed.get("user_id")).longValue());
    assertEquals(1001L, ((Number) parsed.get("tenant_id")).longValue());
    assertEquals("sid-1", parsed.get("sid", String.class));
    assertEquals("jti-1", parsed.getId());
  }

  @Test
  void verify_shouldRejectExpiredToken() throws Exception {
    JwtTokenEngine engine = buildEngine(1);
    TokenClaims claims =
        TokenClaims.builder()
            .userId(1L)
            .tenantId(2L)
            .tenantCode("tenant-a")
            .preferredUsername("sunny")
            .email("sunny@datapillar.ai")
            .tokenType("access")
            .build();

    String token = engine.issueAccessToken(claims);
    Thread.sleep(1200L);

    com.sunny.datapillar.common.exception.UnauthorizedException exception =
        assertThrows(
            com.sunny.datapillar.common.exception.UnauthorizedException.class,
            () -> engine.verify(token));
    assertNotNull(exception.getMessage());
  }

  @Test
  void verify_shouldRejectForgedToken() {
    JwtTokenEngine engine = buildEngine(3600);
    TokenClaims claims =
        TokenClaims.builder()
            .userId(1L)
            .tenantId(2L)
            .tenantCode("tenant-a")
            .preferredUsername("sunny")
            .email("sunny@datapillar.ai")
            .tokenType("access")
            .build();

    String token = engine.issueAccessToken(claims);
    String[] tokenParts = token.split("\\.");
    String forgedSignature =
        (tokenParts[2].startsWith("a") ? "b" : "a") + tokenParts[2].substring(1);
    String forgedToken = tokenParts[0] + "." + tokenParts[1] + "." + forgedSignature;

    com.sunny.datapillar.common.exception.UnauthorizedException exception =
        assertThrows(
            com.sunny.datapillar.common.exception.UnauthorizedException.class,
            () -> engine.verify(forgedToken));
    assertNotNull(exception.getMessage());
  }
}
