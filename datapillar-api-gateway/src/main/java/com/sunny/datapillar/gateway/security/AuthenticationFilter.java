package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.security.AuthType;
import com.sunny.datapillar.gateway.config.AuthenticationProperties;
import com.sunny.datapillar.gateway.exception.base.GatewayForbiddenException;
import com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway authentication filter performs strict local token verification and injects trusted
 * identity headers.
 */
@Slf4j
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final List<String> TRUSTED_CONTEXT_HEADERS =
      List.of(
          HeaderConstants.HEADER_PRINCIPAL_TYPE,
          HeaderConstants.HEADER_PRINCIPAL_ID,
          HeaderConstants.HEADER_USER_ID,
          HeaderConstants.HEADER_TENANT_ID,
          HeaderConstants.HEADER_TENANT_CODE,
          HeaderConstants.HEADER_USERNAME,
          HeaderConstants.HEADER_EMAIL,
          HeaderConstants.HEADER_USER_ROLES,
          HeaderConstants.HEADER_PRINCIPAL_ISS,
          HeaderConstants.HEADER_PRINCIPAL_SUB,
          HeaderConstants.HEADER_ACTOR_USER_ID,
          HeaderConstants.HEADER_ACTOR_TENANT_ID,
          HeaderConstants.HEADER_IMPERSONATION);

  private final AuthenticationProperties properties;
  private final AccessTokenVerifier accessTokenVerifier;
  private final ApiKeyAuthenticationResolver apiKeyAuthenticationResolver;
  private final RouteAuthTypeResolver routeAuthTypeResolver;
  private final ClientIpResolver clientIpResolver;

  public AuthenticationFilter(
      AuthenticationProperties properties,
      AccessTokenVerifier accessTokenVerifier,
      ApiKeyAuthenticationResolver apiKeyAuthenticationResolver,
      RouteAuthTypeResolver routeAuthTypeResolver,
      ClientIpResolver clientIpResolver) {
    this.properties = properties;
    this.accessTokenVerifier = accessTokenVerifier;
    this.apiKeyAuthenticationResolver = apiKeyAuthenticationResolver;
    this.routeAuthTypeResolver = routeAuthTypeResolver;
    this.clientIpResolver = clientIpResolver;
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
    if (routeAuthTypeResolver.isPublicPath(path)) {
      return chain.filter(exchange);
    }
    AuthType authType = routeAuthTypeResolver.resolve(path);
    if (authType == null) {
      return chain.filter(exchange);
    }

    rejectClientTrustedHeaders(request);
    String traceId = request.getHeaders().getFirst(HeaderConstants.HEADER_TRACE_ID);
    return resolveCredential(authType, request, traceId)
        .flatMap(verifiedToken -> forwardTrustedIdentity(exchange, chain, verifiedToken));
  }

  private Mono<VerifiedAccessToken> resolveCredential(
      AuthType authType, ServerHttpRequest request, String traceId) {
    return switch (authType) {
      case JWT -> {
        String token = extractJwtToken(request);
        if (!StringUtils.hasText(token)) {
          yield Mono.error(new GatewayUnauthorizedException("Missing authentication information"));
        }
        yield accessTokenVerifier.verify(token, traceId);
      }
      case API_KEY -> {
        String apiKey = extractApiKey(request);
        if (!StringUtils.hasText(apiKey)) {
          yield Mono.error(new GatewayUnauthorizedException("Missing authentication information"));
        }
        yield apiKeyAuthenticationResolver.resolve(
            apiKey, clientIpResolver.resolve(request), traceId);
      }
    };
  }

  private Mono<Void> forwardTrustedIdentity(
      ServerWebExchange exchange, GatewayFilterChain chain, VerifiedAccessToken verifiedToken) {
    String username = trimToNull(verifiedToken.username());
    String email = trimToNull(verifiedToken.email());

    ServerHttpRequest mutatedRequest =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers -> {
                  sanitizeContextHeaders(headers);
                  headers.set(
                      HeaderConstants.HEADER_PRINCIPAL_TYPE, verifiedToken.principalType().name());
                  headers.set(HeaderConstants.HEADER_PRINCIPAL_ID, verifiedToken.principalId());
                  headers.set(HeaderConstants.HEADER_PRINCIPAL_ISS, verifiedToken.issuer());
                  headers.set(HeaderConstants.HEADER_PRINCIPAL_SUB, verifiedToken.subject());
                  if (verifiedToken.userId() != null) {
                    headers.set(
                        HeaderConstants.HEADER_USER_ID, String.valueOf(verifiedToken.userId()));
                  }
                  headers.set(
                      HeaderConstants.HEADER_TENANT_ID, String.valueOf(verifiedToken.tenantId()));
                  headers.set(HeaderConstants.HEADER_TENANT_CODE, verifiedToken.tenantCode());
                  headers.set(
                      HeaderConstants.HEADER_IMPERSONATION,
                      String.valueOf(verifiedToken.impersonation()));
                  if (StringUtils.hasText(username)) {
                    headers.set(HeaderConstants.HEADER_USERNAME, username);
                  }
                  if (StringUtils.hasText(email)) {
                    headers.set(HeaderConstants.HEADER_EMAIL, email);
                  }
                  if (!verifiedToken.roles().isEmpty()) {
                    headers.set(
                        HeaderConstants.HEADER_USER_ROLES, String.join(",", verifiedToken.roles()));
                  }
                  if (verifiedToken.impersonation()) {
                    headers.set(
                        HeaderConstants.HEADER_ACTOR_USER_ID,
                        String.valueOf(verifiedToken.actorUserId()));
                    headers.set(
                        HeaderConstants.HEADER_ACTOR_TENANT_ID,
                        String.valueOf(verifiedToken.actorTenantId()));
                  }
                })
            .build();

    String traceId =
        trimToNull(exchange.getRequest().getHeaders().getFirst(HeaderConstants.HEADER_TRACE_ID));
    log.info(
        "security_event event=trusted_identity_injected principal_type={} principal_id={} iss={} sub={} user_id={} tenant_id={} tenant_code={} impersonation={} trace_id={}",
        verifiedToken.principalType(),
        verifiedToken.principalId(),
        verifiedToken.issuer(),
        verifiedToken.subject(),
        verifiedToken.userId(),
        verifiedToken.tenantId(),
        verifiedToken.tenantCode(),
        verifiedToken.impersonation(),
        traceId == null ? "" : traceId);

    return chain.filter(exchange.mutate().request(mutatedRequest).build());
  }

  private void rejectClientTrustedHeaders(ServerHttpRequest request) {
    for (String headerName : TRUSTED_CONTEXT_HEADERS) {
      if (StringUtils.hasText(request.getHeaders().getFirst(headerName))) {
        throw new GatewayForbiddenException("Client trusted identity headers are not allowed");
      }
    }
  }

  private String extractJwtToken(ServerHttpRequest request) {
    String bearerToken =
        extractBearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    String cookieToken = extractCookieToken(request);
    if (bearerToken != null && cookieToken != null) {
      throw new GatewayUnauthorizedException("Multiple authentication credentials are not allowed");
    }
    if (bearerToken != null) {
      return bearerToken;
    }
    return cookieToken;
  }

  private String extractApiKey(ServerHttpRequest request) {
    String cookieToken = extractCookieToken(request);
    if (cookieToken != null) {
      throw new GatewayUnauthorizedException("Cookie authentication is not allowed for API_KEY");
    }
    return extractBearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
  }

  private String extractBearerToken(String authorization) {
    String normalizedAuthorization = trimToNull(authorization);
    if (normalizedAuthorization == null) {
      return null;
    }
    if (!normalizedAuthorization.startsWith(BEARER_PREFIX)) {
      throw new GatewayUnauthorizedException("Invalid Authorization header");
    }
    String token = normalizedAuthorization.substring(BEARER_PREFIX.length()).trim();
    if (!StringUtils.hasText(token)) {
      throw new GatewayUnauthorizedException("Invalid Authorization header");
    }
    return token;
  }

  private String extractCookieToken(ServerHttpRequest request) {
    List<HttpCookie> cookies = request.getCookies().get(properties.getAuthTokenCookieName());
    if (cookies == null || cookies.isEmpty()) {
      return null;
    }
    String token = trimToNull(cookies.get(0).getValue());
    return StringUtils.hasText(token) ? token : null;
  }

  private void sanitizeContextHeaders(HttpHeaders headers) {
    headers.remove(HeaderConstants.HEADER_PRINCIPAL_TYPE);
    headers.remove(HeaderConstants.HEADER_PRINCIPAL_ID);
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

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  @Override
  public int getOrder() {
    return -120;
  }
}
