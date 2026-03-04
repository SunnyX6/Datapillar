package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.gateway.config.AuthenticationProperties;
import com.sunny.datapillar.gateway.exception.base.GatewayForbiddenException;
import com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

class AuthenticationFilterTest {

  @Test
  void shouldRejectClientSuppliedTenantHeaders() {
    ReactiveJwtDecoder jwtDecoder = Mockito.mock(ReactiveJwtDecoder.class);
    AuthenticationFilter filter = new AuthenticationFilter(createProperties(), jwtDecoder);
    Jwt jwt = createJwt();
    Mockito.when(jwtDecoder.decode("valid-token")).thenReturn(Mono.just(jwt));

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/studio/jobs")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header(HeaderConstants.HEADER_TENANT_CODE, "t-hacker")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayForbiddenException exception =
        Assertions.assertThrows(
            GatewayForbiddenException.class, () -> filter.filter(exchange, emptyChain()).block());
    Assertions.assertEquals("Client tenant headers are not allowed", exception.getMessage());
  }

  @Test
  void shouldInjectTrustedHeadersFromJwtClaims() {
    ReactiveJwtDecoder jwtDecoder = Mockito.mock(ReactiveJwtDecoder.class);
    AuthenticationFilter filter = new AuthenticationFilter(createProperties(), jwtDecoder);
    Jwt jwt = createJwt();
    Mockito.when(jwtDecoder.decode("valid-token")).thenReturn(Mono.just(jwt));

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/studio/jobs")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header(HeaderConstants.HEADER_USER_ID, "9999")
            .header(HeaderConstants.HEADER_USERNAME, "spoofed")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    AtomicReference<ServerHttpRequest> mutatedRequest = new AtomicReference<>();

    GatewayFilterChain chain =
        chainExchange -> {
          mutatedRequest.set(chainExchange.getRequest());
          return Mono.empty();
        };

    filter.filter(exchange, chain).block();

    ServerHttpRequest forwardedRequest = mutatedRequest.get();
    Assertions.assertNotNull(forwardedRequest);
    Assertions.assertEquals(
        "https://auth.datapillar.local",
        forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_PRINCIPAL_ISS));
    Assertions.assertEquals(
        "subject-101",
        forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_PRINCIPAL_SUB));
    Assertions.assertEquals(
        "101", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_USER_ID));
    Assertions.assertEquals(
        "1001", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_TENANT_ID));
    Assertions.assertEquals(
        "t-1001", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_TENANT_CODE));
    Assertions.assertEquals(
        "sunny", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_USERNAME));
    Assertions.assertEquals(
        "sunny@datapillar.ai",
        forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_EMAIL));
    Assertions.assertEquals(
        "ADMIN,DEVELOPER",
        forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_USER_ROLES));
  }

  @Test
  void shouldRejectWhenTenantCodeClaimMissing() {
    ReactiveJwtDecoder jwtDecoder = Mockito.mock(ReactiveJwtDecoder.class);
    AuthenticationFilter filter = new AuthenticationFilter(createProperties(), jwtDecoder);
    Jwt jwt =
        Jwt.withTokenValue("valid-token")
            .header("alg", "none")
            .issuedAt(Instant.parse("2026-03-04T00:00:00Z"))
            .expiresAt(Instant.parse("2026-03-04T01:00:00Z"))
            .claim("iss", "https://auth.datapillar.local")
            .claim("sub", "subject-101")
            .claim("aud", List.of("datapillar-api"))
            .claim("user_id", 101)
            .claim("tenant_id", 1001)
            .build();
    Mockito.when(jwtDecoder.decode("valid-token")).thenReturn(Mono.just(jwt));

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/studio/jobs")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayUnauthorizedException exception =
        Assertions.assertThrows(
            GatewayUnauthorizedException.class,
            () -> filter.filter(exchange, emptyChain()).block());
    Assertions.assertEquals("Missing tenant context", exception.getMessage());
  }

  private AuthenticationProperties createProperties() {
    AuthenticationProperties properties = new AuthenticationProperties();
    properties.setEnabled(true);
    properties.setAudience("datapillar-api");
    properties.setProtectedPathPrefixes(List.of("/api/studio", "/api/ai"));
    properties.setPublicPathPrefixes(List.of("/api/auth"));
    properties.setUsernameClaim("preferred_username");
    properties.setEmailClaim("email");
    return properties;
  }

  private GatewayFilterChain emptyChain() {
    return exchange -> Mono.empty();
  }

  private Jwt createJwt() {
    return Jwt.withTokenValue("valid-token")
        .header("alg", "none")
        .issuedAt(Instant.parse("2026-03-04T00:00:00Z"))
        .expiresAt(Instant.parse("2026-03-04T01:00:00Z"))
        .claim("iss", "https://auth.datapillar.local")
        .claim("sub", "subject-101")
        .claim("aud", List.of("datapillar-api"))
        .claim("user_id", 101)
        .claim("tenant_id", 1001)
        .claim("tenant_code", "t-1001")
        .claim("preferred_username", "sunny")
        .claim("email", "sunny@datapillar.ai")
        .claim("roles", List.of("admin", "developer"))
        .build();
  }
}
