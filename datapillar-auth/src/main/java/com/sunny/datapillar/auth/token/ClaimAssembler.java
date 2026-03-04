package com.sunny.datapillar.auth.token;

import com.sunny.datapillar.common.exception.BadRequestException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Unified claim assembler for access and refresh tokens. */
@Component
public class ClaimAssembler {

  public Map<String, Object> assemble(TokenClaims tokenClaims) {
    if (tokenClaims == null) {
      throw new BadRequestException("token claims cannot be empty");
    }
    if (!"access".equals(tokenClaims.getTokenType())
        && !"refresh".equals(tokenClaims.getTokenType())) {
      throw new BadRequestException("token_type must be access or refresh");
    }

    Map<String, Object> claims = new LinkedHashMap<>();

    claims.put("iss", tokenClaims.getIssuer());
    claims.put("sub", tokenClaims.getSubject());
    claims.put("aud", tokenClaims.getAudience());
    claims.put(
        "exp",
        tokenClaims.getExpiration() == null ? null : tokenClaims.getExpiration().getEpochSecond());
    claims.put(
        "iat",
        tokenClaims.getIssuedAt() == null ? null : tokenClaims.getIssuedAt().getEpochSecond());
    claims.put(
        "nbf",
        tokenClaims.getNotBefore() == null ? null : tokenClaims.getNotBefore().getEpochSecond());
    claims.put("jti", tokenClaims.getTokenId());

    claims.put("sid", tokenClaims.getSessionId());
    claims.put("user_id", tokenClaims.getUserId());
    claims.put("tenant_id", tokenClaims.getTenantId());
    claims.put("tenant_code", tokenClaims.getTenantCode());
    claims.put("tenant_codes", normalizeTenantCodes(tokenClaims));
    claims.put("preferred_username", tokenClaims.getPreferredUsername());
    claims.put("email", tokenClaims.getEmail());
    claims.put("roles", tokenClaims.getRoles());
    claims.put("impersonation", Boolean.TRUE.equals(tokenClaims.getImpersonation()));
    claims.put("actor_user_id", tokenClaims.getActorUserId());
    claims.put("actor_tenant_id", tokenClaims.getActorTenantId());
    claims.put("token_type", tokenClaims.getTokenType());

    return claims;
  }

  public Map<String, Object> assembleBusinessClaims(TokenClaims tokenClaims) {
    Map<String, Object> assembled = assemble(tokenClaims);
    assembled.remove("aud");
    assembled.remove("iss");
    assembled.remove("sub");
    assembled.remove("exp");
    assembled.remove("iat");
    assembled.remove("nbf");
    assembled.remove("jti");
    return assembled;
  }

  private List<String> normalizeTenantCodes(TokenClaims tokenClaims) {
    if (tokenClaims.getTenantCodes() != null && !tokenClaims.getTenantCodes().isEmpty()) {
      return tokenClaims.getTenantCodes();
    }
    if (tokenClaims.getTenantCode() != null && !tokenClaims.getTenantCode().isBlank()) {
      return List.of(tokenClaims.getTenantCode());
    }
    return List.of();
  }
}
