package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.gateway.config.AuthenticationProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway authentication filter Performs local JWT validation and writes trusted identity headers.
 */
@Slf4j
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

  private final AuthenticationProperties properties;
  private final ReactiveJwtDecoder jwtDecoder;

  public AuthenticationFilter(
      AuthenticationProperties properties, ClientIpResolver clientIpResolver) {
    this.properties = properties;
    this.jwtDecoder = buildDecoder(properties);
  }

  AuthenticationFilter(AuthenticationProperties properties, ReactiveJwtDecoder jwtDecoder) {
    this.properties = properties;
    this.jwtDecoder = jwtDecoder;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!properties.isEnabled()) {
      return chain.filter(exchange);
    }

    ServerHttpRequest request = exchange.getRequest();
    String path = request.getPath().value();
    if (request.getMethod() != null && "OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
      return chain.filter(exchange);
    }
    if (isPublicPath(path) || !isProtectedPath(path)) {
      return chain.filter(exchange);
    }

    String token = extractToken(request);
    if (!StringUtils.hasText(token)) {
      return Mono.error(
          new com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException(
              "Missing authentication information"));
    }

    return jwtDecoder
        .decode(token)
        .onErrorMap(
            JwtException.class,
            ex ->
                new com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException(
                    "Invalid token"))
        .flatMap(jwt -> handleAuthenticated(exchange, chain, jwt));
  }

  private Mono<Void> handleAuthenticated(
      ServerWebExchange exchange, GatewayFilterChain chain, Jwt jwt) {
    validateAudience(jwt);
    rejectClientTenantHeaders(exchange.getRequest());

    String issuer = trimToNull(jwt.getIssuer() == null ? null : jwt.getIssuer().toString());
    String subject = trimToNull(jwt.getSubject());
    if (!StringUtils.hasText(issuer) || !StringUtils.hasText(subject)) {
      return Mono.error(
          new com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException(
              "Authentication subject missing"));
    }

    Long userId = requirePositiveLong(jwt.getClaim("user_id"), "Missing user context");
    Long tenantId = requirePositiveLong(jwt.getClaim("tenant_id"), "Missing tenant context");
    String tenantCode =
        requireClaimText(jwt.getClaimAsString("tenant_code"), "Missing tenant context");
    String username = trimToNull(jwt.getClaimAsString(properties.getUsernameClaim()));
    if (!StringUtils.hasText(username)) {
      username = subject;
    }
    final String finalUsername = username;
    final String finalEmail = trimToNull(jwt.getClaimAsString(properties.getEmailClaim()));
    final List<String> roleCodes = normalizeRoleCodes(jwt.getClaims());

    ServerHttpRequest mutatedRequest =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers -> {
                  sanitizeContextHeaders(headers);
                  headers.set(HeaderConstants.HEADER_PRINCIPAL_ISS, issuer);
                  headers.set(HeaderConstants.HEADER_PRINCIPAL_SUB, subject);
                  headers.set(HeaderConstants.HEADER_TENANT_ID, String.valueOf(tenantId));
                  headers.set(HeaderConstants.HEADER_TENANT_CODE, tenantCode);
                  headers.set(HeaderConstants.HEADER_USER_ID, String.valueOf(userId));
                  if (StringUtils.hasText(finalUsername)) {
                    headers.set(HeaderConstants.HEADER_USERNAME, finalUsername);
                  }
                  if (StringUtils.hasText(finalEmail)) {
                    headers.set(HeaderConstants.HEADER_EMAIL, finalEmail);
                  }
                  if (!roleCodes.isEmpty()) {
                    headers.set(HeaderConstants.HEADER_USER_ROLES, String.join(",", roleCodes));
                  }
                })
            .build();

    String traceId =
        trimToNull(exchange.getRequest().getHeaders().getFirst(HeaderConstants.HEADER_TRACE_ID));
    log.info(
        "security_event event=trusted_identity_injected iss={} sub={} preferred_username={} tenant_code={} trace_id={}",
        issuer,
        subject,
        finalUsername,
        tenantCode,
        traceId == null ? "" : traceId);

    return chain.filter(exchange.mutate().request(mutatedRequest).build());
  }

  private void rejectClientTenantHeaders(ServerHttpRequest request) {
    String clientTenantId =
        trimToNull(request.getHeaders().getFirst(HeaderConstants.HEADER_TENANT_ID));
    String clientTenantCode =
        trimToNull(request.getHeaders().getFirst(HeaderConstants.HEADER_TENANT_CODE));
    if (clientTenantId != null || clientTenantCode != null) {
      throw new com.sunny.datapillar.gateway.exception.base.GatewayForbiddenException(
          "Client tenant headers are not allowed");
    }
  }

  private void validateAudience(Jwt jwt) {
    String expectedAudience = trimToNull(properties.getAudience());
    if (!StringUtils.hasText(expectedAudience)) {
      return;
    }
    List<String> audiences = jwt.getAudience();
    if (audiences == null || audiences.stream().noneMatch(expectedAudience::equals)) {
      throw new com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException(
          "Token audience mismatch");
    }
  }

  private ReactiveJwtDecoder buildDecoder(AuthenticationProperties properties) {
    String issuerUri = trimToNull(properties.getIssuerUri());
    String jwkSetUri = trimToNull(properties.getJwkSetUri());
    if (StringUtils.hasText(issuerUri)) {
      return ReactiveJwtDecoders.fromIssuerLocation(issuerUri);
    }
    if (StringUtils.hasText(jwkSetUri)) {
      return NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }
    throw new IllegalStateException(
        "security.authentication.issuer-uri or security.authentication.jwk-set-uri must be configured");
  }

  private String extractToken(ServerHttpRequest request) {
    String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }

    List<HttpCookie> cookies = request.getCookies().get(properties.getAuthTokenCookieName());
    if (cookies == null || cookies.isEmpty()) {
      return null;
    }
    HttpCookie cookie = cookies.get(0);
    return cookie.getValue();
  }

  private boolean isPublicPath(String path) {
    for (String prefix : properties.getPublicPathPrefixes()) {
      if (path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private boolean isProtectedPath(String path) {
    for (String prefix : properties.getProtectedPathPrefixes()) {
      if (path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private String requireClaimText(String value, String message) {
    String normalized = trimToNull(value);
    if (!StringUtils.hasText(normalized)) {
      throw new com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException(message);
    }
    return normalized;
  }

  private Long requirePositiveLong(Object value, String message) {
    if (value == null) {
      throw new com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException(message);
    }
    try {
      long parsed = Long.parseLong(String.valueOf(value));
      if (parsed <= 0) {
        throw new com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException(message);
      }
      return parsed;
    } catch (NumberFormatException ex) {
      throw new com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException(message);
    }
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private List<String> normalizeRoleCodes(Map<String, Object> claims) {
    Object rolesClaim = claims.get("roles");
    return normalizeStringList(rolesClaim);
  }

  private List<String> normalizeStringList(Object value) {
    if (value == null) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    if (value instanceof Collection<?> collection) {
      for (Object item : collection) {
        String normalized = trimToNull(item == null ? null : String.valueOf(item));
        if (StringUtils.hasText(normalized)) {
          result.add(normalized.toUpperCase());
        }
      }
      return result;
    }
    if (value instanceof String text) {
      Arrays.stream(text.split(","))
          .map(this::trimToNull)
          .filter(StringUtils::hasText)
          .map(String::toUpperCase)
          .forEach(result::add);
      return result;
    }
    return List.of();
  }

  private void sanitizeContextHeaders(HttpHeaders headers) {
    headers.remove(HeaderConstants.HEADER_USER_ID);
    headers.remove(HeaderConstants.HEADER_TENANT_ID);
    headers.remove(HeaderConstants.HEADER_TENANT_CODE);
    headers.remove(HeaderConstants.HEADER_USERNAME);
    headers.remove(HeaderConstants.HEADER_EMAIL);
    headers.remove(HeaderConstants.HEADER_USER_ROLES);
    headers.remove(HeaderConstants.HEADER_PRINCIPAL_ISS);
    headers.remove(HeaderConstants.HEADER_PRINCIPAL_SUB);
    headers.remove(HeaderConstants.HEADER_ACTOR_USER_ID);
    headers.remove(HeaderConstants.HEADER_ACTOR_TENANT_ID);
    headers.remove(HeaderConstants.HEADER_IMPERSONATION);
  }

  @Override
  public int getOrder() {
    return -120;
  }
}
