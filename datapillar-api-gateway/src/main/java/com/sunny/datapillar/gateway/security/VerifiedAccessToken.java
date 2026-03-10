package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.common.security.PrincipalType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Verified gateway access-token snapshot. */
public record VerifiedAccessToken(
    PrincipalType principalType,
    String principalId,
    String issuer,
    String subject,
    String sessionId,
    String tokenId,
    Long userId,
    Long tenantId,
    String tenantCode,
    String username,
    String email,
    List<String> roles,
    boolean impersonation,
    Long actorUserId,
    Long actorTenantId) {

  public VerifiedAccessToken {
    if (principalType == null) {
      throw new IllegalArgumentException("principalType must not be null");
    }
    LinkedHashSet<String> normalizedRoles = new LinkedHashSet<>();
    if (roles != null) {
      for (String role : roles) {
        if (role == null) {
          continue;
        }
        String normalized = role.trim();
        if (!normalized.isEmpty()) {
          normalizedRoles.add(normalized.toUpperCase(Locale.ROOT));
        }
      }
    }
    roles = List.copyOf(normalizedRoles);
  }
}
