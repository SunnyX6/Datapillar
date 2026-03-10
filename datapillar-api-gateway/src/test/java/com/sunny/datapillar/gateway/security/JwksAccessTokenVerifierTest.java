package com.sunny.datapillar.gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.common.response.ErrorResponse;
import com.sunny.datapillar.common.security.Ed25519JwkSupport;
import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.gateway.config.AuthenticationProperties;
import com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class JwksAccessTokenVerifierTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldVerifyValidAccessToken() throws Exception {
    KeyPair keyPair = io.jsonwebtoken.security.Jwks.CRV.Ed25519.keyPair().build();
    AuthenticationProperties properties = createProperties();
    IssuerJwksProvider jwksProvider =
        new IssuerJwksProvider(
            properties, webClientBuilder(HttpStatus.OK, jwksBody(keyPair, "kid-1")));
    AuthAuthenticationContextClient authenticationContextClient =
        new AuthAuthenticationContextClient(
            properties,
            webClientBuilderExpectTrace(
                HttpStatus.OK, authContextBody("sid-1", "jti-1", 101L, 1001L, true), "trace-123"));
    JwksAccessTokenVerifier verifier =
        new JwksAccessTokenVerifier(
            properties, jwksProvider, authenticationContextClient, objectMapper);
    String token = signAccessToken(keyPair, "kid-1", "sid-1", "jti-1");

    VerifiedAccessToken verifiedToken = verifier.verify(token, "trace-123").block();

    assertEquals("https://auth.datapillar.local", verifiedToken.issuer());
    assertEquals("subject-101", verifiedToken.subject());
    assertEquals(PrincipalType.USER, verifiedToken.principalType());
    assertEquals("user:101", verifiedToken.principalId());
    assertEquals("sid-1", verifiedToken.sessionId());
    assertEquals("jti-1", verifiedToken.tokenId());
    assertEquals(101L, verifiedToken.userId());
    assertEquals(1001L, verifiedToken.tenantId());
    assertEquals("t-1001", verifiedToken.tenantCode());
    assertEquals(List.of("ADMIN", "DEVELOPER"), verifiedToken.roles());
    assertTrue(verifiedToken.impersonation());
    assertEquals(1L, verifiedToken.actorUserId());
    assertEquals(0L, verifiedToken.actorTenantId());
  }

  @Test
  void shouldRefreshJwksWhenKidMissing() throws Exception {
    KeyPair oldKeyPair = io.jsonwebtoken.security.Jwks.CRV.Ed25519.keyPair().build();
    KeyPair newKeyPair = io.jsonwebtoken.security.Jwks.CRV.Ed25519.keyPair().build();
    AuthenticationProperties properties = createProperties();
    AtomicInteger counter = new AtomicInteger();
    WebClient.Builder jwksWebClientBuilder =
        WebClient.builder()
            .exchangeFunction(
                request -> {
                  String body =
                      counter.getAndIncrement() == 0
                          ? jwksBody(oldKeyPair, "kid-old")
                          : jwksBody(newKeyPair, "kid-new");
                  return Mono.just(jsonResponse(HttpStatus.OK, body));
                });

    IssuerJwksProvider jwksProvider = new IssuerJwksProvider(properties, jwksWebClientBuilder);
    AuthAuthenticationContextClient authenticationContextClient =
        new AuthAuthenticationContextClient(
            properties,
            webClientBuilder(HttpStatus.OK, authContextBody("sid-1", "jti-1", 101L, 1001L, true)));
    JwksAccessTokenVerifier verifier =
        new JwksAccessTokenVerifier(
            properties, jwksProvider, authenticationContextClient, objectMapper);
    String token = signAccessToken(newKeyPair, "kid-new", "sid-1", "jti-1");

    VerifiedAccessToken verifiedToken = verifier.verify(token, null).block();

    assertEquals("jti-1", verifiedToken.tokenId());
    assertEquals(2, counter.get());
  }

  @Test
  void shouldRejectWhenAuthRejectsToken() throws Exception {
    KeyPair keyPair = io.jsonwebtoken.security.Jwks.CRV.Ed25519.keyPair().build();
    AuthenticationProperties properties = createProperties();
    IssuerJwksProvider jwksProvider =
        new IssuerJwksProvider(
            properties, webClientBuilder(HttpStatus.OK, jwksBody(keyPair, "kid-1")));
    AuthAuthenticationContextClient authenticationContextClient =
        new AuthAuthenticationContextClient(
            properties,
            webClientBuilder(
                HttpStatus.UNAUTHORIZED,
                errorBody(
                    HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED, "Token is no longer valid")));
    JwksAccessTokenVerifier verifier =
        new JwksAccessTokenVerifier(
            properties, jwksProvider, authenticationContextClient, objectMapper);
    String token = signAccessToken(keyPair, "kid-1", "sid-1", "jti-1");

    GatewayUnauthorizedException exception =
        assertThrows(
            GatewayUnauthorizedException.class, () -> verifier.verify(token, null).block());
    assertEquals("Token is no longer valid", exception.getMessage());
  }

  @Test
  void shouldRejectWhenAuthContextMismatchesValidatedToken() throws Exception {
    KeyPair keyPair = io.jsonwebtoken.security.Jwks.CRV.Ed25519.keyPair().build();
    AuthenticationProperties properties = createProperties();
    IssuerJwksProvider jwksProvider =
        new IssuerJwksProvider(
            properties, webClientBuilder(HttpStatus.OK, jwksBody(keyPair, "kid-1")));
    AuthAuthenticationContextClient authenticationContextClient =
        new AuthAuthenticationContextClient(
            properties,
            webClientBuilder(HttpStatus.OK, authContextBody("sid-1", "jti-2", 101L, 1001L, true)));
    JwksAccessTokenVerifier verifier =
        new JwksAccessTokenVerifier(
            properties, jwksProvider, authenticationContextClient, objectMapper);
    String token = signAccessToken(keyPair, "kid-1", "sid-1", "jti-1");

    GatewayUnauthorizedException exception =
        assertThrows(
            GatewayUnauthorizedException.class, () -> verifier.verify(token, null).block());
    assertEquals("Invalid token", exception.getMessage());
  }

  @Test
  void shouldRejectRefreshTokenOnGateway() throws Exception {
    KeyPair keyPair = io.jsonwebtoken.security.Jwks.CRV.Ed25519.keyPair().build();
    AuthenticationProperties properties = createProperties();
    IssuerJwksProvider jwksProvider =
        new IssuerJwksProvider(
            properties, webClientBuilder(HttpStatus.OK, jwksBody(keyPair, "kid-1")));
    AtomicInteger authCalls = new AtomicInteger();
    AuthAuthenticationContextClient authenticationContextClient =
        new AuthAuthenticationContextClient(
            properties,
            WebClient.builder()
                .exchangeFunction(
                    request -> {
                      authCalls.incrementAndGet();
                      return Mono.just(
                          jsonResponse(
                              HttpStatus.OK, authContextBody("sid-1", "jti-1", 101L, 1001L, true)));
                    }));
    JwksAccessTokenVerifier verifier =
        new JwksAccessTokenVerifier(
            properties, jwksProvider, authenticationContextClient, objectMapper);
    String token = signToken(keyPair, "kid-1", "sid-1", "jti-1", "refresh");

    GatewayUnauthorizedException exception =
        assertThrows(
            GatewayUnauthorizedException.class, () -> verifier.verify(token, null).block());
    assertEquals("Invalid token type", exception.getMessage());
    assertEquals(0, authCalls.get());
  }

  @Test
  void shouldRejectTamperedSignatureBeforeCallingAuth() throws Exception {
    KeyPair keyPair = io.jsonwebtoken.security.Jwks.CRV.Ed25519.keyPair().build();
    AuthenticationProperties properties = createProperties();
    IssuerJwksProvider jwksProvider =
        new IssuerJwksProvider(
            properties, webClientBuilder(HttpStatus.OK, jwksBody(keyPair, "kid-1")));
    AtomicInteger authCalls = new AtomicInteger();
    AuthAuthenticationContextClient authenticationContextClient =
        new AuthAuthenticationContextClient(
            properties,
            WebClient.builder()
                .exchangeFunction(
                    request -> {
                      authCalls.incrementAndGet();
                      return Mono.just(
                          jsonResponse(
                              HttpStatus.OK, authContextBody("sid-1", "jti-1", 101L, 1001L, true)));
                    }));
    JwksAccessTokenVerifier verifier =
        new JwksAccessTokenVerifier(
            properties, jwksProvider, authenticationContextClient, objectMapper);
    String token = tamperSignature(signAccessToken(keyPair, "kid-1", "sid-1", "jti-1"));

    GatewayUnauthorizedException exception =
        assertThrows(
            GatewayUnauthorizedException.class, () -> verifier.verify(token, null).block());
    assertEquals("Invalid token", exception.getMessage());
    assertEquals(0, authCalls.get());
  }

  @Test
  void shouldFailStartupWhenJwksUnavailable() {
    AuthenticationProperties properties = createProperties();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                new IssuerJwksProvider(
                    properties, webClientBuilder(HttpStatus.SERVICE_UNAVAILABLE, "{}")));
    assertTrue(exception.getMessage().contains("Failed to load auth JWKS from issuer"));
  }

  private AuthenticationProperties createProperties() {
    AuthenticationProperties properties = new AuthenticationProperties();
    properties.setIssuer("https://auth.datapillar.local");
    properties.setAudience("datapillar-api");
    properties.setUsernameClaim("preferred_username");
    properties.setEmailClaim("email");
    properties.setJwksCacheSeconds(300);
    return properties;
  }

  private WebClient.Builder webClientBuilder(HttpStatus status, String body) {
    ExchangeFunction exchangeFunction = request -> Mono.just(jsonResponse(status, body));
    return WebClient.builder().exchangeFunction(exchangeFunction);
  }

  private WebClient.Builder webClientBuilderExpectTrace(
      HttpStatus status, String body, String expectedTraceId) {
    ExchangeFunction exchangeFunction =
        request -> {
          assertEquals(
              expectedTraceId, request.headers().getFirst(HeaderConstants.HEADER_TRACE_ID));
          return Mono.just(jsonResponse(status, body));
        };
    return WebClient.builder().exchangeFunction(exchangeFunction);
  }

  private ClientResponse jsonResponse(HttpStatus status, String body) {
    return ClientResponse.create(status)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(body)
        .build();
  }

  private String jwksBody(KeyPair keyPair, String kid) {
    try {
      return objectMapper.writeValueAsString(
          Map.of("keys", List.of(Ed25519JwkSupport.toJwk(kid, keyPair.getPublic()))));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to serialize JWKS test payload", ex);
    }
  }

  private String authContextBody(
      String sid, String jti, Long userId, Long tenantId, boolean impersonation) {
    try {
      Map<String, Object> context = new LinkedHashMap<>();
      context.put("principalType", "USER");
      context.put("principalId", "user:101");
      context.put("userId", userId);
      context.put("tenantId", tenantId);
      context.put("tenantCode", "t-1001");
      context.put("tenantName", "tenant-1001");
      context.put("username", "sunny");
      context.put("email", "sunny@datapillar.ai");
      context.put("roles", List.of("admin", "developer"));
      context.put("impersonation", impersonation);
      context.put("actorUserId", 1L);
      context.put("actorTenantId", 0L);
      context.put("sessionId", sid);
      context.put("tokenId", jti);
      return objectMapper.writeValueAsString(ApiResponse.ok(context));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to serialize auth context test payload", ex);
    }
  }

  private String errorBody(HttpStatus status, String type, String message) {
    try {
      return objectMapper.writeValueAsString(
          ErrorResponse.of(status.value(), type, message, "trace-auth-1"));
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to serialize error payload", ex);
    }
  }

  private String signAccessToken(KeyPair keyPair, String kid, String sid, String jti) {
    return signToken(keyPair, kid, sid, jti, "access");
  }

  private String signToken(KeyPair keyPair, String kid, String sid, String jti, String tokenType) {
    Instant issuedAt = Instant.now();
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("aud", List.of("datapillar-api"));
    claims.put("sid", sid);
    claims.put("jti", jti);
    claims.put("user_id", 101L);
    claims.put("tenant_id", 1001L);
    claims.put("tenant_code", "t-1001");
    claims.put("preferred_username", "sunny");
    claims.put("email", "sunny@datapillar.ai");
    claims.put("roles", List.of("admin", "developer"));
    claims.put("impersonation", true);
    claims.put("actor_user_id", 1L);
    claims.put("actor_tenant_id", 0L);
    claims.put("token_type", tokenType);
    return Jwts.builder()
        .header()
        .keyId(kid)
        .and()
        .subject("subject-101")
        .issuer("https://auth.datapillar.local")
        .issuedAt(java.util.Date.from(issuedAt))
        .expiration(java.util.Date.from(issuedAt.plusSeconds(3600)))
        .id(jti)
        .claims(claims)
        .signWith(keyPair.getPrivate(), Jwts.SIG.EdDSA)
        .compact();
  }

  private String tamperSignature(String token) {
    String[] tokenParts = token.split("\\.");
    String signature = tokenParts[2];
    String forgedSignature = (signature.startsWith("a") ? "b" : "a") + signature.substring(1);
    return tokenParts[0] + "." + tokenParts[1] + "." + forgedSignature;
  }
}
