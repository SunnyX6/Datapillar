package com.sunny.datapillar.gateway.filter;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import com.sunny.datapillar.gateway.config.GatewayAssertionProperties;
import com.sunny.datapillar.gateway.config.GatewayAuthProperties;
import com.sunny.datapillar.gateway.security.ClientIpResolver;
import com.sunny.datapillar.gateway.security.GatewayAssertionSigner;
import com.sunny.datapillar.gateway.security.SessionStateVerifier;
import com.sunny.datapillar.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AuthGlobalFilterTest {

    private JwtUtil jwtUtil;
    private GatewayAssertionSigner assertionSigner;
    private GatewayAssertionProperties assertionProperties;
    private SessionStateVerifier sessionStateVerifier;
    private ClientIpResolver clientIpResolver;
    private GatewayAuthProperties authProperties;

    @BeforeEach
    void setUp() {
        jwtUtil = Mockito.mock(JwtUtil.class);
        assertionSigner = Mockito.mock(GatewayAssertionSigner.class);
        sessionStateVerifier = Mockito.mock(SessionStateVerifier.class);
        clientIpResolver = Mockito.mock(ClientIpResolver.class);
        assertionProperties = new GatewayAssertionProperties();
        authProperties = new GatewayAuthProperties();
        authProperties.setWhitelist(List.of("/api/login"));
        assertionProperties.setEnabled(true);
        assertionProperties.setHeaderName(HeaderConstants.HEADER_GATEWAY_ASSERTION);
        when(clientIpResolver.resolve(any())).thenReturn("10.0.0.1");

        Claims claims = Jwts.claims()
                .setSubject("1")
                .add("tokenType", "access")
                .add("username", "sunny")
                .add("email", "sunny@datapillar.test")
                .add("tenantId", 10L)
                .add("sid", "sid-1")
                .setId("jti-1")
                .build();
        when(jwtUtil.parseToken("token")).thenReturn(claims);
        when(jwtUtil.getTokenType(claims)).thenReturn("access");
        when(jwtUtil.getUserId(claims)).thenReturn(1L);
        when(jwtUtil.getUsername(claims)).thenReturn("sunny");
        when(jwtUtil.getEmail(claims)).thenReturn("sunny@datapillar.test");
        when(jwtUtil.getTenantId(claims)).thenReturn(10L);
        when(jwtUtil.getActorUserId(claims)).thenReturn(null);
        when(jwtUtil.getActorTenantId(claims)).thenReturn(null);
        when(jwtUtil.isImpersonation(claims)).thenReturn(false);
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("ADMIN"));
        when(jwtUtil.getSessionId(claims)).thenReturn("sid-1");
        when(jwtUtil.getTokenId(claims)).thenReturn("jti-1");
        when(sessionStateVerifier.isAccessTokenActive("sid-1", "jti-1")).thenReturn(reactor.core.publisher.Mono.just(true));
        when(assertionSigner.sign(any())).thenReturn("signed-assertion");
    }

    @Test
    void filter_studioRouteShouldInjectOnlyGatewayAssertion() {
        AuthGlobalFilter filter = new AuthGlobalFilter(
                jwtUtil,
                assertionSigner,
                assertionProperties,
                sessionStateVerifier,
                clientIpResolver,
                authProperties
        );

        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.POST, "/api/studio/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .header(HeaderConstants.HEADER_USER_ID, "999")
                .header(HeaderConstants.HEADER_TENANT_ID, "888")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        GatewayFilterChain chain = chainExchange -> {
            captured.set(chainExchange.getRequest());
            return reactor.core.publisher.Mono.empty();
        };

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = captured.get();
        assertNotNull(forwarded);
        assertEquals("signed-assertion", forwarded.getHeaders().getFirst(HeaderConstants.HEADER_GATEWAY_ASSERTION));
        assertNull(forwarded.getHeaders().getFirst(HeaderConstants.HEADER_USER_ID));
        assertNull(forwarded.getHeaders().getFirst(HeaderConstants.HEADER_TENANT_ID));
        assertNull(forwarded.getHeaders().getFirst(HeaderConstants.HEADER_USERNAME));
        assertEquals("10.0.0.1", forwarded.getHeaders().getFirst("X-Forwarded-For"));
    }

    @Test
    void filter_nonStudioRouteShouldStillStripLegacyHeaders() {
        AuthGlobalFilter filter = new AuthGlobalFilter(
                jwtUtil,
                assertionSigner,
                assertionProperties,
                sessionStateVerifier,
                clientIpResolver,
                authProperties
        );

        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.POST, "/api/ai/workflows")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .header(HeaderConstants.HEADER_USER_ID, "999")
                .header(HeaderConstants.HEADER_TENANT_ID, "888")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        GatewayFilterChain chain = chainExchange -> {
            captured.set(chainExchange.getRequest());
            return reactor.core.publisher.Mono.empty();
        };

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = captured.get();
        assertNotNull(forwarded);
        assertEquals("signed-assertion", forwarded.getHeaders().getFirst(HeaderConstants.HEADER_GATEWAY_ASSERTION));
        assertNull(forwarded.getHeaders().getFirst(HeaderConstants.HEADER_USER_ID));
        assertNull(forwarded.getHeaders().getFirst(HeaderConstants.HEADER_TENANT_ID));
        assertNull(forwarded.getHeaders().getFirst(HeaderConstants.HEADER_USERNAME));
    }

    @Test
    void filter_whitelistPathShouldNormalizeForwardedHeaders() {
        AuthGlobalFilter filter = new AuthGlobalFilter(
                jwtUtil,
                assertionSigner,
                assertionProperties,
                sessionStateVerifier,
                clientIpResolver,
                authProperties
        );

        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.POST, "/api/login")
                .header("X-Forwarded-For", "198.51.100.99")
                .header("X-Real-IP", "198.51.100.98")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        GatewayFilterChain chain = chainExchange -> {
            captured.set(chainExchange.getRequest());
            return reactor.core.publisher.Mono.empty();
        };

        filter.filter(exchange, chain).block();

        ServerHttpRequest forwarded = captured.get();
        assertNotNull(forwarded);
        assertEquals("10.0.0.1", forwarded.getHeaders().getFirst("X-Forwarded-For"));
        assertEquals("10.0.0.1", forwarded.getHeaders().getFirst("X-Real-IP"));
    }

    @Test
    void filter_shouldRejectWhenAssertionDisabled() {
        assertionProperties.setEnabled(false);
        AuthGlobalFilter filter = new AuthGlobalFilter(
                jwtUtil,
                assertionSigner,
                assertionProperties,
                sessionStateVerifier,
                clientIpResolver,
                authProperties
        );

        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.POST, "/api/studio/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        GatewayFilterChain chain = chainExchange -> {
            captured.set(chainExchange.getRequest());
            return reactor.core.publisher.Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertNull(captured.get());
        assertEquals(401, exchange.getResponse().getStatusCode().value());
        String body = exchange.getResponse().getBodyAsString().blockOptional().orElse("");
        assertTrue(body.contains("内部断言未启用"));
    }

    @Test
    void filter_shouldRejectWhenSessionRevoked() {
        when(sessionStateVerifier.isAccessTokenActive("sid-1", "jti-1")).thenReturn(reactor.core.publisher.Mono.just(false));
        AuthGlobalFilter filter = new AuthGlobalFilter(
                jwtUtil,
                assertionSigner,
                assertionProperties,
                sessionStateVerifier,
                clientIpResolver,
                authProperties
        );

        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.POST, "/api/studio/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerHttpRequest> captured = new AtomicReference<>();
        GatewayFilterChain chain = chainExchange -> {
            captured.set(chainExchange.getRequest());
            return reactor.core.publisher.Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertNull(captured.get());
        assertEquals(401, exchange.getResponse().getStatusCode().value());
        String body = exchange.getResponse().getBodyAsString().blockOptional().orElse("");
        assertTrue(body.contains("Token 已失效"));
    }

    @Test
    void filter_shouldExposeBusinessExceptionDetailWhenTokenInvalid() {
        when(jwtUtil.parseToken("token")).thenThrow(new BusinessException(ErrorCode.TOKEN_INVALID));
        AuthGlobalFilter filter = new AuthGlobalFilter(
                jwtUtil,
                assertionSigner,
                assertionProperties,
                sessionStateVerifier,
                clientIpResolver,
                authProperties
        );

        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.POST, "/api/studio/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = chainExchange -> reactor.core.publisher.Mono.empty();
        filter.filter(exchange, chain).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
        String body = exchange.getResponse().getBodyAsString().blockOptional().orElse("");
        assertTrue(body.contains("Token 无效或已过期"));
        assertTrue(body.contains("BusinessException"));
    }
}
