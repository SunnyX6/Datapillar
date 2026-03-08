package com.sunny.datapillar.auth.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClaimAssemblerTest {

  private final ClaimAssembler claimAssembler = new ClaimAssembler();

  @Test
  void assemble_shouldContainSnakeCaseFieldsOnly() {
    TokenClaims claims =
        TokenClaims.builder()
            .issuer("https://auth.datapillar.local")
            .subject("101")
            .audience(List.of("datapillar-api"))
            .issuedAt(Instant.now())
            .notBefore(Instant.now())
            .expiration(Instant.now().plusSeconds(3600))
            .tokenId("jti-1")
            .sessionId("sid-1")
            .userId(101L)
            .tenantId(1001L)
            .tenantCode("tenant-a")
            .tenantCodes(List.of("tenant-a", "tenant-b"))
            .preferredUsername("sunny")
            .email("sunny@datapillar.ai")
            .roles(List.of("ADMIN"))
            .impersonation(false)
            .actorUserId(0L)
            .actorTenantId(0L)
            .tokenType("access")
            .build();

    Map<String, Object> assembled = claimAssembler.assemble(claims);

    assertEquals("https://auth.datapillar.local", assembled.get("iss"));
    assertEquals("101", assembled.get("sub"));
    assertEquals("access", assembled.get("token_type"));
    assertEquals(101L, assembled.get("user_id"));
    assertEquals(1001L, assembled.get("tenant_id"));
    assertEquals("tenant-a", assembled.get("tenant_code"));
    assertEquals("jti-1", assembled.get("jti"));
    assertTrue(assembled.containsKey("sid"));
    assertTrue(assembled.containsKey("aud"));

    assertFalse(assembled.containsKey("userId"));
    assertFalse(assembled.containsKey("tenantId"));
  }

  @Test
  void assembleBusinessClaims_shouldKeepSessionIdentifiers() {
    TokenClaims claims =
        TokenClaims.builder()
            .issuer("https://auth.datapillar.local")
            .subject("101")
            .audience(List.of("datapillar-api"))
            .issuedAt(Instant.now())
            .expiration(Instant.now().plusSeconds(3600))
            .tokenId("jti-1")
            .sessionId("sid-1")
            .userId(101L)
            .tenantId(1001L)
            .tenantCode("tenant-a")
            .tokenType("access")
            .build();

    Map<String, Object> assembled = claimAssembler.assembleBusinessClaims(claims);

    assertEquals("sid-1", assembled.get("sid"));
    assertEquals("jti-1", assembled.get("jti"));
    assertFalse(assembled.containsKey("iss"));
    assertFalse(assembled.containsKey("aud"));
  }

  @Test
  void assemble_shouldRejectUnknownTokenType() {
    TokenClaims claims = TokenClaims.builder().tokenType("unknown").build();

    com.sunny.datapillar.common.exception.BadRequestException exception =
        assertThrows(
            com.sunny.datapillar.common.exception.BadRequestException.class,
            () -> claimAssembler.assemble(claims));

    assertEquals("token_type must be access or refresh", exception.getMessage());
  }
}
