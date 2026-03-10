package com.sunny.datapillar.gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.gateway.config.AuthenticationProperties;
import com.sunny.datapillar.gateway.config.GatewaySecurityProperties;
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
    ApiKeyAuthenticationResolver apiKeyAuthenticationResolver =
        Mockito.mock(ApiKeyAuthenticationResolver.class);
    AuthenticationFilter filter =
        createFilter(accessTokenVerifier, apiKeyAuthenticationResolver, "127.0.0.1");

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
    Mockito.verifyNoInteractions(apiKeyAuthenticationResolver);
  }

  @Test
  void shouldInjectTrustedHeadersFromVerifiedToken() {
    AccessTokenVerifier accessTokenVerifier = Mockito.mock(AccessTokenVerifier.class);
    ApiKeyAuthenticationResolver apiKeyAuthenticationResolver =
        Mockito.mock(ApiKeyAuthenticationResolver.class);
    AuthenticationFilter filter =
        createFilter(accessTokenVerifier, apiKeyAuthenticationResolver, "127.0.0.1");
    VerifiedAccessToken verifiedToken =
        new VerifiedAccessToken(
            PrincipalType.USER,
            "user:101",
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
        "USER", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_PRINCIPAL_TYPE));
    assertEquals(
        "user:101", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_PRINCIPAL_ID));
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
    Mockito.verifyNoInteractions(apiKeyAuthenticationResolver);
  }

  @Test
  void shouldResolveApiKeyForOpenApiRoute() {
    AccessTokenVerifier accessTokenVerifier = Mockito.mock(AccessTokenVerifier.class);
    ApiKeyAuthenticationResolver apiKeyAuthenticationResolver =
        Mockito.mock(ApiKeyAuthenticationResolver.class);
    AuthenticationFilter filter =
        createFilter(accessTokenVerifier, apiKeyAuthenticationResolver, "10.0.0.8");
    VerifiedAccessToken verifiedToken =
        new VerifiedAccessToken(
            PrincipalType.API_KEY,
            "api-key:201",
            "https://auth.datapillar.local",
            "api-key:201",
            null,
            null,
            null,
            1001L,
            "t-1001",
            "lineage-ingest",
            null,
            List.of("ADMIN"),
            false,
            null,
            null);
    Mockito.when(apiKeyAuthenticationResolver.resolve("valid-api-key", "10.0.0.8", null))
        .thenReturn(Mono.just(verifiedToken));

    MockServerHttpRequest request =
        MockServerHttpRequest.post("/openapi/openlineage/events")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-api-key")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    AtomicReference<ServerHttpRequest> mutatedRequest = new AtomicReference<>();

    filter
        .filter(
            exchange,
            chainExchange -> {
              mutatedRequest.set(chainExchange.getRequest());
              return Mono.empty();
            })
        .block();

    ServerHttpRequest forwardedRequest = mutatedRequest.get();
    assertNotNull(forwardedRequest);
    assertEquals(
        "API_KEY", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_PRINCIPAL_TYPE));
    assertEquals(
        "api-key:201", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_PRINCIPAL_ID));
    assertEquals(
        "lineage-ingest", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_USERNAME));
    assertEquals("1001", forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_TENANT_ID));
    assertEquals(null, forwardedRequest.getHeaders().getFirst(HeaderConstants.HEADER_USER_ID));
    Mockito.verifyNoInteractions(accessTokenVerifier);
  }

  @Test
  void shouldRejectApiKeyCredentialOnJwtRoute() {
    AccessTokenVerifier accessTokenVerifier = Mockito.mock(AccessTokenVerifier.class);
    ApiKeyAuthenticationResolver apiKeyAuthenticationResolver =
        Mockito.mock(ApiKeyAuthenticationResolver.class);
    AuthenticationFilter filter =
        createFilter(accessTokenVerifier, apiKeyAuthenticationResolver, "127.0.0.1");
    Mockito.when(accessTokenVerifier.verify("dpk_plaintext_key_1234", null))
        .thenReturn(Mono.error(new GatewayUnauthorizedException("Invalid token")));

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/api/studio/jobs")
            .header(HttpHeaders.AUTHORIZATION, "Bearer dpk_plaintext_key_1234")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayUnauthorizedException exception =
        assertThrows(
            GatewayUnauthorizedException.class,
            () -> filter.filter(exchange, emptyChain()).block());

    assertEquals("Invalid token", exception.getMessage());
    Mockito.verifyNoInteractions(apiKeyAuthenticationResolver);
  }

  @Test
  void shouldRejectJwtCredentialOnApiKeyRoute() {
    AccessTokenVerifier accessTokenVerifier = Mockito.mock(AccessTokenVerifier.class);
    ApiKeyAuthenticationResolver apiKeyAuthenticationResolver =
        Mockito.mock(ApiKeyAuthenticationResolver.class);
    AuthenticationFilter filter =
        createFilter(accessTokenVerifier, apiKeyAuthenticationResolver, "127.0.0.1");
    Mockito.when(apiKeyAuthenticationResolver.resolve("jwt-access-token", "127.0.0.1", null))
        .thenReturn(Mono.error(new GatewayUnauthorizedException("Invalid API key")));

    MockServerHttpRequest request =
        MockServerHttpRequest.get("/openapi/studio/admin/tenant/current/api-keys")
            .header(HttpHeaders.AUTHORIZATION, "Bearer jwt-access-token")
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayUnauthorizedException exception =
        assertThrows(
            GatewayUnauthorizedException.class,
            () -> filter.filter(exchange, emptyChain()).block());

    assertEquals("Invalid API key", exception.getMessage());
    Mockito.verifyNoInteractions(accessTokenVerifier);
  }

  @Test
  void shouldRejectWhenTokenMissing() {
    AccessTokenVerifier accessTokenVerifier = Mockito.mock(AccessTokenVerifier.class);
    ApiKeyAuthenticationResolver apiKeyAuthenticationResolver =
        Mockito.mock(ApiKeyAuthenticationResolver.class);
    AuthenticationFilter filter =
        createFilter(accessTokenVerifier, apiKeyAuthenticationResolver, "127.0.0.1");
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/studio/jobs").build());

    GatewayUnauthorizedException exception =
        assertThrows(
            GatewayUnauthorizedException.class,
            () -> filter.filter(exchange, emptyChain()).block());
    assertEquals("Missing authentication information", exception.getMessage());
    Mockito.verifyNoInteractions(accessTokenVerifier);
    Mockito.verifyNoInteractions(apiKeyAuthenticationResolver);
  }

  @Test
  void shouldRejectWhenBearerAndCookieProvidedTogether() {
    AccessTokenVerifier accessTokenVerifier = Mockito.mock(AccessTokenVerifier.class);
    ApiKeyAuthenticationResolver apiKeyAuthenticationResolver =
        Mockito.mock(ApiKeyAuthenticationResolver.class);
    AuthenticationFilter filter =
        createFilter(accessTokenVerifier, apiKeyAuthenticationResolver, "127.0.0.1");
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
    Mockito.verifyNoInteractions(apiKeyAuthenticationResolver);
  }

  @Test
  void shouldRejectCookieAuthenticationForApiKeyRoute() {
    AccessTokenVerifier accessTokenVerifier = Mockito.mock(AccessTokenVerifier.class);
    ApiKeyAuthenticationResolver apiKeyAuthenticationResolver =
        Mockito.mock(ApiKeyAuthenticationResolver.class);
    AuthenticationFilter filter =
        createFilter(accessTokenVerifier, apiKeyAuthenticationResolver, "127.0.0.1");
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/openapi/studio/admin/tenant/current/api-keys")
            .cookie(new HttpCookie("auth-token", "cookie-token"))
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayUnauthorizedException exception =
        assertThrows(
            GatewayUnauthorizedException.class,
            () -> filter.filter(exchange, emptyChain()).block());
    assertEquals("Cookie authentication is not allowed for API_KEY", exception.getMessage());
    Mockito.verifyNoInteractions(accessTokenVerifier);
    Mockito.verifyNoInteractions(apiKeyAuthenticationResolver);
  }

  private AuthenticationProperties createProperties() {
    AuthenticationProperties properties = new AuthenticationProperties();
    properties.setEnabled(true);
    properties.setAudience("datapillar-api");
    properties.setIssuer("https://auth.datapillar.local");
    properties.setProtectedPathPrefixes(
        List.of("/api/studio", "/api/ai", "/openapi/studio", "/openapi/openlineage"));
    properties.setPublicPathPrefixes(List.of("/api/auth"));
    properties.setUsernameClaim("preferred_username");
    properties.setEmailClaim("email");
    return properties;
  }

  private AuthenticationFilter createFilter(
      AccessTokenVerifier accessTokenVerifier,
      ApiKeyAuthenticationResolver apiKeyAuthenticationResolver,
      String clientIp) {
    GatewaySecurityProperties gatewaySecurityProperties = new GatewaySecurityProperties();
    ClientIpResolver clientIpResolver =
        Mockito.spy(new ClientIpResolver(gatewaySecurityProperties));
    Mockito.doReturn(clientIp).when(clientIpResolver).resolve(Mockito.any());
    AuthenticationProperties properties = createProperties();
    return new AuthenticationFilter(
        properties,
        accessTokenVerifier,
        apiKeyAuthenticationResolver,
        new RouteAuthTypeResolver(properties),
        clientIpResolver);
  }

  private GatewayFilterChain emptyChain() {
    return exchange -> Mono.empty();
  }
}
