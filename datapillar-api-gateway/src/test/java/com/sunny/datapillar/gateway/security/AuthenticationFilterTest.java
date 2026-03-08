package com.sunny.datapillar.gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.gateway.config.AuthenticationProperties;
import com.sunny.datapillar.gateway.exception.base.GatewayForbiddenException;
import com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class AuthenticationFilterTest {

  @Test
  void shouldRejectClientSuppliedTrustedHeaders() {
    AccessTokenVerifier accessTokenVerifier = Mockito.mock(AccessTokenVerifier.class);
    AuthenticationFilter filter = new AuthenticationFilter(createProperties(), accessTokenVerifier);

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/studio/jobs")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .header(HeaderConstants.HEADER_TENANT_CODE, "t-hacker")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayForbiddenException exception =
        assertThrows(
            GatewayForbiddenException.class, () -> filter.filter(exchange, emptyChain()).block());
    assertEquals("Client trusted identity headers are not allowed", exception.getMessage());
    Mockito.verifyNoInteractions(accessTokenVerifier);
  }

  @Test
  void shouldInjectTrustedHeadersFromVerifiedToken() {
    AccessTokenVerifier accessTokenVerifier = Mockito.mock(AccessTokenVerifier.class);
    AuthenticationFilter filter = new AuthenticationFilter(createProperties(), accessTokenVerifier);
    VerifiedAccessToken verifiedToken =
        new VerifiedAccessToken(
            "https://auth.datapillar.local",
            "subject-101",
            "sid-1",
            "jti-1",
            101L,
            1001L,
            "t-1001",
            "sunny",
            "sunny@datapillar.ai",
            List.of("admin", "developer"),
            true,
            1L,
            0L);
    Mockito.when(accessTokenVerifier.verify("valid-token", null))
        .thenReturn(Mono.just(verifiedToken));

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/studio/jobs")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
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
    assertNotNull(forwardedRequest);
    assertEquals(
        "https://auth.datapillar.local",
        forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_PRINCIPAL_ISS));
    assertEquals(
        "subject-101",
        forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_PRINCIPAL_SUB));
    assertEquals("101", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_USER_ID));
    assertEquals("1001", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_TENANT_ID));
    assertEquals(
        "t-1001", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_TENANT_CODE));
    assertEquals("sunny", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_USERNAME));
    assertEquals(
        "sunny@datapillar.ai",
        forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_EMAIL));
    assertEquals(
        "ADMIN,DEVELOPER",
        forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_USER_ROLES));
    assertEquals(
        "true", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_IMPERSONATION));
    assertEquals("1", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_ACTOR_USER_ID));
    assertEquals(
        "0", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_ACTOR_TENANT_ID));
  }

  @Test
  void shouldRejectWhenTokenMissing() {
    AccessTokenVerifier accessTokenVerifier = Mockito.mock(AccessTokenVerifier.class);
    AuthenticationFilter filter = new AuthenticationFilter(createProperties(), accessTokenVerifier);
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/studio/jobs").build());

    GatewayUnauthorizedException exception =
        assertThrows(
            GatewayUnauthorizedException.class,
            () -> filter.filter(exchange, emptyChain()).block());
    assertEquals("Missing authentication information", exception.getMessage());
    Mockito.verifyNoInteractions(accessTokenVerifier);
  }

  @Test
  void shouldRejectWhenBearerAndCookieProvidedTogether() {
    AccessTokenVerifier accessTokenVerifier = Mockito.mock(AccessTokenVerifier.class);
    AuthenticationFilter filter = new AuthenticationFilter(createProperties(), accessTokenVerifier);
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/studio/jobs")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .cookie(new HttpCookie("auth-token", "cookie-token"))
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayUnauthorizedException exception =
        assertThrows(
            GatewayUnauthorizedException.class,
            () -> filter.filter(exchange, emptyChain()).block());
    assertEquals("Multiple authentication credentials are not allowed", exception.getMessage());
    Mockito.verifyNoInteractions(accessTokenVerifier);
  }

  private AuthenticationProperties createProperties() {
    AuthenticationProperties properties = new AuthenticationProperties();
    properties.setEnabled(true);
    properties.setAudience("datapillar-api");
    properties.setIssuer("https://auth.datapillar.local");
    properties.setProtectedPathPrefixes(List.of("/api/studio", "/api/ai"));
    properties.setPublicPathPrefixes(List.of("/api/auth"));
    properties.setUsernameClaim("preferred_username");
    properties.setEmailClaim("email");
    return properties;
  }

  private GatewayFilterChain emptyChain() {
    return exchange -> Mono.empty();
  }
}
