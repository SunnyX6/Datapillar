package com.sunny.datapillar.gateway.filter;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.gateway.config.GatewaySecurityProperties;
import com.sunny.datapillar.gateway.security.TokenHashUtil;
import com.sunny.datapillar.gateway.util.JwtUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class CsrfGlobalFilter implements GlobalFilter, Ordered {

    private static final String AUTH_COOKIE_NAME = "auth-token";
    private static final String REFRESH_COOKIE_NAME = "refresh-token";
    private static final String CSRF_KEY_PREFIX = "auth:csrf:token:";
    private static final String REFRESH_CSRF_KEY_PREFIX = "auth:csrf:refresh:";

    private final GatewaySecurityProperties securityProperties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public CsrfGlobalFilter(GatewaySecurityProperties securityProperties,
                            ReactiveStringRedisTemplate redisTemplate,
                            JwtUtil jwtUtil) {
        this.securityProperties = securityProperties;
        this.redisTemplate = redisTemplate;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!securityProperties.getCsrf().isEnabled()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        if (isSafeMethod(request.getMethod())) {
            return chain.filter(exchange);
        }
        String path = request.getPath().value();
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        if (hasAuthorizationHeader(request)) {
            return chain.filter(exchange);
        }

        HttpCookie authCookie = request.getCookies().getFirst(AUTH_COOKIE_NAME);
        HttpCookie refreshCookie = request.getCookies().getFirst(REFRESH_COOKIE_NAME);
        String authCookieValue = authCookie == null ? null : authCookie.getValue();
        String refreshCookieValue = refreshCookie == null ? null : refreshCookie.getValue();
        if ((authCookieValue == null || authCookieValue.isBlank())
                && (refreshCookieValue == null || refreshCookieValue.isBlank())) {
            return chain.filter(exchange);
        }

        if (!isOriginAllowed(request)) {
            return forbidden(exchange, "CSRF 校验失败");
        }

        boolean refreshRequest = pathMatcher.match("/api/auth/refresh", path);
        String headerName = refreshRequest
                ? securityProperties.getCsrf().getRefreshHeaderName()
                : securityProperties.getCsrf().getHeaderName();
        String cookieName = refreshRequest
                ? securityProperties.getCsrf().getRefreshCookieName()
                : securityProperties.getCsrf().getCookieName();
        String headerToken = request.getHeaders().getFirst(headerName);
        HttpCookie csrfCookie = request.getCookies().getFirst(cookieName);
        if (headerToken == null || csrfCookie == null || !headerToken.equals(csrfCookie.getValue())) {
            return forbidden(exchange, "CSRF 校验失败");
        }

        String tenantId = request.getHeaders().getFirst(HeaderConstants.HEADER_TENANT_ID);
        String userId = request.getHeaders().getFirst(HeaderConstants.HEADER_USER_ID);
        if (tenantId == null || userId == null) {
            TokenIdentity tokenIdentity = resolveTokenIdentity(path, authCookieValue, refreshCookieValue);
            if (tokenIdentity != null) {
                tenantId = tokenIdentity.tenantId();
                userId = tokenIdentity.userId();
            }
        }
        if (tenantId == null || userId == null) {
            return forbidden(exchange, "CSRF 校验失败");
        }

        String keyPrefix = refreshRequest ? REFRESH_CSRF_KEY_PREFIX : CSRF_KEY_PREFIX;
        String key = keyPrefix + tenantId + ":" + userId;
        String tokenHash = TokenHashUtil.sha256(headerToken);

        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(value -> {
                    if (value == null || !value.equals(tokenHash)) {
                        return forbidden(exchange, "CSRF 校验失败");
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(forbidden(exchange, "CSRF 校验失败"));
    }

    private boolean isSafeMethod(HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.HEAD || method == HttpMethod.OPTIONS;
    }

    private boolean isWhitelisted(String path) {
        List<String> whitelist = securityProperties.getCsrf().getWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        return whitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern.trim(), path));
    }

    /**
     * refresh 请求优先使用 refresh-token，避免 access-token 到期后 CSRF 身份解析失败。
     */
    private TokenIdentity resolveTokenIdentity(String path, String authCookie, String refreshCookie) {
        if (pathMatcher.match("/api/auth/refresh", path)) {
            TokenIdentity refreshIdentity = parseIdentity(refreshCookie);
            if (refreshIdentity != null) {
                return refreshIdentity;
            }
            return parseIdentity(authCookie);
        }

        TokenIdentity authIdentity = parseIdentity(authCookie);
        if (authIdentity != null) {
            return authIdentity;
        }
        return parseIdentity(refreshCookie);
    }

    private TokenIdentity parseIdentity(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Long tenantId = jwtUtil.getTenantId(token);
            Long userId = jwtUtil.getUserId(token);
            if (tenantId == null || userId == null) {
                return null;
            }
            return new TokenIdentity(String.valueOf(tenantId), String.valueOf(userId));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasAuthorizationHeader(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return authHeader != null && authHeader.startsWith("Bearer ");
    }

    private boolean isOriginAllowed(ServerHttpRequest request) {
        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        if (origin == null || origin.isBlank()) {
            String referer = request.getHeaders().getFirst(HttpHeaders.REFERER);
            origin = extractOrigin(referer);
        }
        if (origin == null || origin.isBlank()) {
            return false;
        }
        List<String> allowedOrigins = securityProperties.getAllowedOrigins();
        return allowedOrigins != null && allowedOrigins.contains(origin);
    }

    private String extractOrigin(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            int port = uri.getPort();
            if (port > 0) {
                return uri.getScheme() + "://" + uri.getHost() + ":" + port;
            }
            return uri.getScheme() + "://" + uri.getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        byte[] bytes = String.format("{\"code\":403,\"message\":\"%s\"}", message).getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -90;
    }

    private record TokenIdentity(String tenantId, String userId) {
    }
}
