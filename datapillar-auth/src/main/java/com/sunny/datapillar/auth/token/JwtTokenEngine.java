package com.sunny.datapillar.auth.token;

import com.sunny.datapillar.auth.security.JwtToken;
import io.jsonwebtoken.Claims;
import java.util.Map;
import org.springframework.stereotype.Component;

/** EdDSA JWT token engine implementation. */
@Component
public class JwtTokenEngine implements TokenEngine, TokenIssuer, TokenVerifier {

  private final JwtToken jwtToken;
  private final ClaimAssembler claimAssembler;

  public JwtTokenEngine(JwtToken jwtToken, ClaimAssembler claimAssembler) {
    this.jwtToken = jwtToken;
    this.claimAssembler = claimAssembler;
  }

  @Override
  public String issueAccessToken(TokenClaims claims) {
    TokenClaims accessClaims = ensureType(claims, "access");
    Map<String, Object> businessClaims = claimAssembler.assembleBusinessClaims(accessClaims);
    return jwtToken.generateAccessToken(
        accessClaims.getUserId(),
        accessClaims.getTenantId(),
        accessClaims.getTenantCode(),
        accessClaims.getPreferredUsername(),
        accessClaims.getEmail(),
        businessClaims);
  }

  @Override
  public String issueRefreshToken(TokenClaims claims) {
    TokenClaims refreshClaims = ensureType(claims, "refresh");
    return jwtToken.generateRefreshToken(
        refreshClaims.getUserId(),
        refreshClaims.getTenantId(),
        refreshClaims.getTenantCode(),
        Boolean.TRUE.equals(refreshClaims.getRememberMe()),
        refreshClaims.getSessionId(),
        refreshClaims.getTokenId());
  }

  @Override
  public Claims verify(String token) {
    return jwtToken.parseToken(token);
  }

  @Override
  public long accessTokenTtlSeconds() {
    return jwtToken.getAccessTokenExpiration();
  }

  @Override
  public long refreshTokenTtlSeconds(boolean rememberMe) {
    return jwtToken.getRefreshTokenExpiration(rememberMe);
  }

  @Override
  public String issue(TokenClaims claims) {
    if ("refresh".equals(claims.getTokenType())) {
      return issueRefreshToken(claims);
    }
    return issueAccessToken(claims);
  }

  private TokenClaims ensureType(TokenClaims claims, String expectedType) {
    if (claims == null) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(
          "token claims cannot be empty");
    }
    if (!expectedType.equals(claims.getTokenType())) {
      throw new com.sunny.datapillar.common.exception.BadRequestException(
          "token_type must be %s", expectedType);
    }
    return claims;
  }
}
