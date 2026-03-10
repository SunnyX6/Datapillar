package com.sunny.datapillar.gateway.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.security.EdDsaJwtSupport;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.gateway.config.AuthenticationProperties;
import com.sunny.datapillar.gateway.exception.base.GatewayServiceUnavailableException;
import com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/** Access-token verifier backed by issuer JWKS and auth-owned session context. */
@Slf4j
@Component
public class JwksAccessTokenVerifier implements AccessTokenVerifier {

  private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE =
      new TypeReference<>() {};

  private final AuthenticationProperties properties;
  private final IssuerJwksProvider jwksProvider;
  private final AuthAuthenticationContextClient authenticationContextClient;
  private final ObjectMapper objectMapper;

  public JwksAccessTokenVerifier(
      AuthenticationProperties properties,
      IssuerJwksProvider jwksProvider,
      AuthAuthenticationContextClient authenticationContextClient,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.jwksProvider = jwksProvider;
    this.authenticationContextClient = authenticationContextClient;
    this.objectMapper = objectMapper;
    this.properties.validate();
  }

  @Override
  public Mono<VerifiedAccessToken> verify(String token, String traceId) {
    JwtHeader header = readHeader(token);
    if (!"EdDSA".equals(header.algorithm())) {
      return Mono.error(new GatewayUnauthorizedException("Token algorithm mismatch"));
    }

    return jwksProvider
        .resolve(header.keyId(), traceId)
        .map(publicKey -> parseClaims(token, publicKey, header))
        .flatMap(
            validatedJwt ->
                authenticationContextClient
                    .resolve(token, traceId)
                    .map(context -> buildVerifiedToken(validatedJwt, context)))
        .onErrorMap(
            throwable -> {
              if (throwable
                  instanceof com.sunny.datapillar.common.exception.DatapillarRuntimeException) {
                return throwable;
              }
              log.error(
                  "security_event event=token_session_validation_failed reason={}",
                  throwable.getMessage(),
                  throwable);
              return new GatewayServiceUnavailableException(
                  throwable, "Authentication context validation failed");
            });
  }

  private JwtHeader readHeader(String token) {
    if (!StringUtils.hasText(token)) {
      throw new GatewayUnauthorizedException("Missing authentication information");
    }
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw new GatewayUnauthorizedException("Invalid token");
    }
    try {
      byte[] headerBytes = Base64.getUrlDecoder().decode(parts[0]);
      Map<String, Object> header =
          objectMapper.readValue(new String(headerBytes, StandardCharsets.UTF_8), MAP_TYPE);
      String algorithm = requireText(header.get("alg"), "Invalid token");
      String keyId = requireText(header.get("kid"), "Invalid token");
      return new JwtHeader(algorithm, keyId);
    } catch (IllegalArgumentException | java.io.IOException ex) {
      throw new GatewayUnauthorizedException("Invalid token");
    }
  }

  private ValidatedJwt parseClaims(String token, PublicKey publicKey, JwtHeader header) {
    Jws<Claims> signedClaims;
    try {
      signedClaims = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
    } catch (ExpiredJwtException ex) {
      throw new GatewayUnauthorizedException("Token has expired");
    } catch (JwtException | IllegalArgumentException ex) {
      throw new GatewayUnauthorizedException("Invalid token");
    }

    String tokenAlgorithm =
        requireText(trimToNull(signedClaims.getHeader().getAlgorithm()), "Invalid token");
    String keyId = requireText(trimToNull(signedClaims.getHeader().getKeyId()), "Invalid token");
    if (!header.algorithm().equals(tokenAlgorithm) || !header.keyId().equals(keyId)) {
      throw new GatewayUnauthorizedException("Invalid token");
    }

    Claims claims = signedClaims.getPayload();
    String issuer = requireText(trimToNull(claims.getIssuer()), "Invalid token");
    if (!properties.getIssuer().trim().equals(issuer)) {
      throw new GatewayUnauthorizedException("Token issuer mismatch");
    }
    if (!EdDsaJwtSupport.hasAudience(claims, properties.getAudience())) {
      throw new GatewayUnauthorizedException("Token audience mismatch");
    }

    String tokenType = requireText(claims.get("token_type", String.class), "Invalid token");
    if (!"access".equals(tokenType)) {
      throw new GatewayUnauthorizedException("Invalid token type");
    }

    String subject = requireText(trimToNull(claims.getSubject()), "Authentication subject missing");
    String sessionId = requireText(claims.get("sid", String.class), "Invalid token");
    String tokenId = requireText(resolveTokenId(claims), "Invalid token");

    return new ValidatedJwt(issuer, subject, sessionId, tokenId);
  }

  private VerifiedAccessToken buildVerifiedToken(
      ValidatedJwt validatedJwt, AuthAuthenticationContext authenticationContext) {
    String sessionId = requireText(authenticationContext.getSessionId(), "Invalid token");
    String tokenId = requireText(authenticationContext.getTokenId(), "Invalid token");
    if (!validatedJwt.sessionId().equals(sessionId) || !validatedJwt.tokenId().equals(tokenId)) {
      throw new GatewayUnauthorizedException("Invalid token");
    }

    Long userId = requirePositiveLong(authenticationContext.getUserId(), "Missing user context");
    Long tenantId =
        requirePositiveLong(authenticationContext.getTenantId(), "Missing tenant context");
    String tenantCode =
        requireText(authenticationContext.getTenantCode(), "Missing tenant context");
    String username = trimToNull(authenticationContext.getUsername());
    String email = trimToNull(authenticationContext.getEmail());
    boolean impersonation = Boolean.TRUE.equals(authenticationContext.getImpersonation());
    Long actorUserId = null;
    Long actorTenantId = null;
    if (impersonation) {
      actorUserId =
          requirePositiveLong(authenticationContext.getActorUserId(), "Missing actor context");
      actorTenantId =
          requireNonNegativeLong(authenticationContext.getActorTenantId(), "Missing actor context");
    }

    List<String> roles =
        authenticationContext.getRoles() == null ? List.of() : authenticationContext.getRoles();
    PrincipalType principalType = PrincipalType.fromValue(authenticationContext.getPrincipalType());
    if (principalType != null && principalType != PrincipalType.USER) {
      throw new GatewayUnauthorizedException("Invalid token");
    }
    String principalId = trimToNull(authenticationContext.getPrincipalId());
    if (principalId == null) {
      principalId = "user:" + userId;
    }
    return new VerifiedAccessToken(
        PrincipalType.USER,
        principalId,
        validatedJwt.issuer(),
        validatedJwt.subject(),
        sessionId,
        tokenId,
        userId,
        tenantId,
        tenantCode,
        username,
        email,
        roles,
        impersonation,
        actorUserId,
        actorTenantId);
  }

  private String resolveTokenId(Claims claims) {
    String jti = trimToNull(claims.getId());
    if (jti != null) {
      return jti;
    }
    return trimToNull(claims.get("jti", String.class));
  }

  private String requireText(Object value, String message) {
    if (!(value instanceof String text)) {
      throw new GatewayUnauthorizedException(message);
    }
    String normalized = trimToNull(text);
    if (!StringUtils.hasText(normalized)) {
      throw new GatewayUnauthorizedException(message);
    }
    return normalized;
  }

  private Long requirePositiveLong(Object value, String message) {
    Long parsed = parseLong(value);
    if (parsed == null || parsed <= 0L) {
      throw new GatewayUnauthorizedException(message);
    }
    return parsed;
  }

  private Long requireNonNegativeLong(Object value, String message) {
    Long parsed = parseLong(value);
    if (parsed == null || parsed < 0L) {
      throw new GatewayUnauthorizedException(message);
    }
    return parsed;
  }

  private Long parseLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text) {
      try {
        return Long.parseLong(text.trim());
      } catch (NumberFormatException ex) {
        return null;
      }
    }
    return null;
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private record ValidatedJwt(String issuer, String subject, String sessionId, String tokenId) {}

  private record JwtHeader(String algorithm, String keyId) {}
}
