package com.sunny.datapillar.auth.token;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/** Unified token claim contract. */
@Data
@Builder
public class TokenClaims {

  private String issuer;
  private String subject;
  private List<String> audience;
  private Instant issuedAt;
  private Instant notBefore;
  private Instant expiration;
  private String tokenId;

  private String sessionId;
  private Long userId;
  private Long tenantId;
  private String tenantCode;
  private List<String> tenantCodes;
  private String preferredUsername;
  private String email;
  private List<String> roles;
  private Boolean impersonation;
  private Long actorUserId;
  private Long actorTenantId;
  private String tokenType;
  private Boolean rememberMe;
}
