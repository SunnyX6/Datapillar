package com.sunny.datapillar.auth.security;

import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.security.EdDsaJwtSupport;
import com.sunny.datapillar.common.security.SessionTokenClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * JWT token component.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class JwtToken {

  private final PrivateKey privateKey;
  private final PublicKey publicKey;
  private final String issuer;
  private final List<String> audiences;
  private final String algorithm;
  private final String activeKid;
  private final long accessTokenExpiration;
  private final long refreshTokenExpiration;
  private final long refreshTokenRememberExpiration;
  private final long loginTokenExpiration;

  public JwtToken(AuthProperties authProperties) {
    AuthProperties.Token token = authProperties.getToken();
    AuthProperties.Jwks jwks = authProperties.getJwks();
    this.privateKey = EdDsaJwtSupport.loadPrivateKey(token.getPrivateKeyPath());
    this.publicKey = EdDsaJwtSupport.loadPublicKey(token.getPublicKeyPath());
    this.issuer = token.getIssuer();
    this.audiences = normalizeAudiences(token.getAudience());
    this.algorithm = token.getAlgorithm();
    this.activeKid = jwks.getActiveKid();
    this.accessTokenExpiration = token.getAccessTtlSeconds() * 1000;
    this.refreshTokenExpiration = token.getRefreshTtlSeconds() * 1000;
    this.refreshTokenRememberExpiration = token.getRefreshRememberTtlSeconds() * 1000;
    this.loginTokenExpiration = token.getLoginTtlSeconds() * 1000;

    if (!"EdDSA".equalsIgnoreCase(algorithm)) {
      throw new IllegalStateException("auth.token.algorithm must be EdDSA");
    }
    if (!StringUtils.hasText(issuer)) {
      throw new IllegalStateException("auth.token.issuer cannot be empty");
    }
    if (audiences.isEmpty()) {
      throw new IllegalStateException("auth.token.audience cannot be empty");
    }
    if (!StringUtils.hasText(activeKid)) {
      throw new IllegalStateException("auth.jwks.active_kid cannot be empty");
    }
  }

  /** Build an access token. */
  public String generateAccessToken(
      Long userId, Long tenantId, String tenantCode, String username, String email) {
    return generateAccessToken(userId, tenantId, tenantCode, username, email, null);
  }

  /** Build an access token with optional additional claims. */
  public String generateAccessToken(
      Long userId,
      Long tenantId,
      String tenantCode,
      String username,
      String email,
      Map<String, Object> extraClaims) {
    requirePositiveLong(userId, "user_id");
    requirePositiveLong(tenantId, "tenant_id");
    if (!StringUtils.hasText(tenantCode)) {
      throw new BadRequestException("tenant_code cannot be empty");
    }
    Map<String, Object> claims = new HashMap<>();
    claims.put("user_id", userId);
    claims.put("tenant_id", tenantId);
    claims.put("tenant_code", tenantCode.trim());
    claims.put("tenant_codes", List.of(tenantCode.trim()));
    claims.put("preferred_username", username);
    claims.put("email", email);
    claims.put("token_type", "access");
    claims.put("aud", audiences);
    if (extraClaims != null && !extraClaims.isEmpty()) {
      claims.putAll(extraClaims);
    }

    String sid = normalizeClaim(claims.get(SessionTokenClaims.SESSION_ID));
    String jti = normalizeClaim(claims.get(SessionTokenClaims.TOKEN_ID));
    if (sid == null) {
      sid = UUID.randomUUID().toString();
    }
    if (jti == null) {
      jti = UUID.randomUUID().toString();
    }
    claims.put(SessionTokenClaims.SESSION_ID, sid);
    claims.put(SessionTokenClaims.TOKEN_ID, jti);

    Date issuedAt = new Date();
    Date notBefore = issuedAt;
    Date expiration = new Date(issuedAt.getTime() + accessTokenExpiration);
    return sign(claims, String.valueOf(userId), issuedAt, notBefore, expiration, jti);
  }

  /**
   * Build a refresh token.
   *
   * @param userId user ID
   * @param rememberMe remember-me flag (true = 30 days, false = 7 days)
   */
  public String generateRefreshToken(Long userId, Long tenantId, Boolean rememberMe) {
    return generateRefreshToken(userId, tenantId, rememberMe, null, null);
  }

  public String generateRefreshToken(
      Long userId, Long tenantId, Boolean rememberMe, String sessionId, String tokenId) {
    return generateRefreshToken(userId, tenantId, null, rememberMe, sessionId, tokenId);
  }

  public String generateRefreshToken(
      Long userId,
      Long tenantId,
      String tenantCode,
      Boolean rememberMe,
      String sessionId,
      String tokenId) {
    requirePositiveLong(userId, "user_id");
    requirePositiveLong(tenantId, "tenant_id");
    Map<String, Object> claims = new HashMap<>();
    claims.put("token_type", "refresh");
    claims.put("tenant_id", tenantId);
    if (StringUtils.hasText(tenantCode)) {
      claims.put("tenant_code", tenantCode.trim());
    }
    claims.put("remember_me", rememberMe != null && rememberMe);
    claims.put("aud", audiences);

    String sid = normalizeClaim(sessionId);
    String jti = normalizeClaim(tokenId);
    if (sid == null) {
      sid = UUID.randomUUID().toString();
    }
    if (jti == null) {
      jti = UUID.randomUUID().toString();
    }
    claims.put(SessionTokenClaims.SESSION_ID, sid);
    claims.put(SessionTokenClaims.TOKEN_ID, jti);

    Date now = new Date();
    long refreshExpiration =
        rememberMe != null && rememberMe ? refreshTokenRememberExpiration : refreshTokenExpiration;
    Date expiration = new Date(now.getTime() + refreshExpiration);

    return sign(claims, String.valueOf(userId), now, now, expiration, jti);
  }

  /** Build a temporary login token used for tenant selection. */
  public String generateLoginToken(Long userId, Boolean rememberMe) {
    requirePositiveLong(userId, "user_id");
    Map<String, Object> claims = new HashMap<>();
    claims.put("token_type", "login");
    claims.put("remember_me", rememberMe != null && rememberMe);
    claims.put("aud", audiences);

    Date now = new Date();
    Date expiration = new Date(now.getTime() + loginTokenExpiration);

    return sign(claims, String.valueOf(userId), now, now, expiration, UUID.randomUUID().toString());
  }

  /** Get access token expiration in seconds. */
  public long getAccessTokenExpiration() {
    return accessTokenExpiration / 1000;
  }

  /** Get refresh token expiration in seconds. */
  public long getRefreshTokenExpiration(boolean rememberMe) {
    long expiration = rememberMe ? refreshTokenRememberExpiration : refreshTokenExpiration;
    return expiration / 1000;
  }

  public Claims parseToken(String token) {
    if (!StringUtils.hasText(token)) {
      throw new UnauthorizedException("TokenInvalid");
    }
    try {
      JwtParserBuilder parser = Jwts.parser().verifyWith(publicKey);
      parser = parser.requireIssuer(issuer);
      Claims claims = parser.build().parseSignedClaims(token).getPayload();
      validateAudience(claims);
      return claims;
    } catch (ExpiredJwtException ex) {
      throw new UnauthorizedException("TokenExpired");
    } catch (JwtException | IllegalArgumentException ex) {
      throw new UnauthorizedException("TokenInvalid", ex.getMessage());
    }
  }

  public Long getUserId(Claims claims) {
    if (claims == null) {
      return null;
    }
    Long userId = parseLong(claims.get("user_id"));
    if (userId != null) {
      return userId;
    }
    return parseLong(claims.getSubject());
  }

  public Long getTenantId(Claims claims) {
    if (claims == null) {
      return null;
    }
    return parseLong(claims.get("tenant_id"));
  }

  public String getTenantCode(Claims claims) {
    if (claims == null) {
      return null;
    }
    return normalizeClaim(claims.get("tenant_code"));
  }

  public String getUsername(Claims claims) {
    if (claims == null) {
      return null;
    }
    return claims.get("preferred_username", String.class);
  }

  public String getEmail(Claims claims) {
    if (claims == null) {
      return null;
    }
    return claims.get("email", String.class);
  }

  public String getTokenType(Claims claims) {
    if (claims == null) {
      return null;
    }
    return claims.get("token_type", String.class);
  }

  public Boolean getRememberMe(Claims claims) {
    if (claims == null) {
      return null;
    }
    return claims.get("remember_me", Boolean.class);
  }

  public Long getActorUserId(Claims claims) {
    if (claims == null) {
      return null;
    }
    return parseLong(claims.get("actor_user_id"));
  }

  public Long getActorTenantId(Claims claims) {
    if (claims == null) {
      return null;
    }
    return parseLong(claims.get("actor_tenant_id"));
  }

  public boolean isImpersonation(Claims claims) {
    if (claims == null) {
      return false;
    }
    return Boolean.TRUE.equals(claims.get("impersonation", Boolean.class));
  }

  public String getSessionId(Claims claims) {
    if (claims == null) {
      return null;
    }
    return claims.get(SessionTokenClaims.SESSION_ID, String.class);
  }

  public String getTokenId(Claims claims) {
    if (claims == null) {
      return null;
    }
    if (StringUtils.hasText(claims.getId())) {
      return claims.getId();
    }
    return claims.get(SessionTokenClaims.TOKEN_ID, String.class);
  }

  public String getIssuer() {
    return issuer;
  }

  public List<String> getAudiences() {
    return List.copyOf(audiences);
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }

  public PrivateKey getPrivateKey() {
    return privateKey;
  }

  public String getActiveKid() {
    return activeKid;
  }

  private String sign(
      Map<String, Object> claims,
      String subject,
      Date issuedAt,
      Date notBefore,
      Date expiration,
      String tokenId) {
    if (claims == null || !StringUtils.hasText(subject) || issuedAt == null || expiration == null) {
      throw new BadRequestException("Parameter error");
    }
    return Jwts.builder()
        .header()
        .keyId(activeKid)
        .and()
        .claims(claims)
        .subject(subject)
        .issuer(issuer)
        .issuedAt(issuedAt)
        .notBefore(notBefore)
        .expiration(expiration)
        .id(tokenId)
        .signWith(privateKey, Jwts.SIG.EdDSA)
        .compact();
  }

  private List<String> normalizeAudiences(String audienceRaw) {
    List<String> result = new ArrayList<>();
    if (!StringUtils.hasText(audienceRaw)) {
      return result;
    }
    for (String token : audienceRaw.split(",")) {
      String normalized = token == null ? null : token.trim();
      if (StringUtils.hasText(normalized)) {
        result.add(normalized);
      }
    }
    return result;
  }

  private void validateAudience(Claims claims) {
    Object rawAud = claims.get("aud");
    List<String> tokenAudiences = new ArrayList<>();
    if (rawAud instanceof String text && StringUtils.hasText(text)) {
      tokenAudiences.add(text.trim());
    } else if (rawAud instanceof java.util.Collection<?> collection) {
      for (Object item : collection) {
        if (item != null && StringUtils.hasText(item.toString())) {
          tokenAudiences.add(item.toString().trim());
        }
      }
    }
    if (tokenAudiences.isEmpty()) {
      throw new UnauthorizedException("TokenInvalid", "Token audience missing");
    }
    boolean matched =
        audiences.stream().anyMatch(expected -> tokenAudiences.stream().anyMatch(expected::equals));
    if (!matched) {
      throw new UnauthorizedException("TokenInvalid", "Token audience mismatch");
    }
  }

  private void requirePositiveLong(Long value, String field) {
    if (value == null || value <= 0) {
      throw new BadRequestException("%s cannot be empty", field);
    }
  }

  private Long parseLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text) {
      try {
        return Long.parseLong(text);
      } catch (NumberFormatException ex) {
        return null;
      }
    }
    return null;
  }

  private String normalizeClaim(Object value) {
    if (value == null) {
      return null;
    }
    String text = value.toString().trim();
    return text.isEmpty() ? null : text;
  }
}
