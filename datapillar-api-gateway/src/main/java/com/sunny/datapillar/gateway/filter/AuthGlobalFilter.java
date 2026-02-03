package com.sunny.datapillar.gateway.filter;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局认证过滤器
 * 验证 JWT Token，并将用户信息注入请求头传递给下游服务
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("#{'${gateway.auth.whitelist}'.split(',')}")
    private List<String> whitelist;

    public AuthGlobalFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * Cookie 名称常量
     */
    private static final String AUTH_COOKIE_NAME = "auth-token";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        log.info("AuthFilter processing path: {}, whitelist: {}", path, whitelist);

        // 白名单路径直接放行
        if (isWhitelisted(path)) {
            log.info("Path {} is whitelisted, passing through", path);
            return chain.filter(exchange);
        }

        // 提取 Token：优先从 Authorization Header 获取，其次从 Cookie 获取
        String token = extractToken(request);
        if (token == null) {
            log.warn("认证失败 - 缺少认证信息: {}", path);
            return unauthorized(exchange, "缺少认证信息");
        }

        try {
            // 验证 Token
            if (!jwtUtil.isValid(token)) {
                log.warn("认证失败 - Token 无效或已过期: {}", path);
                return unauthorized(exchange, "Token 无效或已过期");
            }

            // 检查是否是 access token
            String tokenType = jwtUtil.getTokenType(token);
            if (!"access".equals(tokenType)) {
                log.warn("认证失败 - Token 类型错误: {}", tokenType);
                return unauthorized(exchange, "Token 类型错误");
            }

            // 提取用户信息
            Long userId = jwtUtil.getUserId(token);
            String username = jwtUtil.getUsername(token);
            String email = jwtUtil.getEmail(token);
            Long tenantId = jwtUtil.getTenantId(token);
            if (tenantId == null) {
                log.warn("认证失败 - Token 缺少租户信息: {}", path);
                return unauthorized(exchange, "Token 缺少租户信息");
            }

            Long actorUserId = jwtUtil.getActorUserId(token);
            Long actorTenantId = jwtUtil.getActorTenantId(token);
            boolean impersonation = jwtUtil.isImpersonation(token);

            // 将用户信息注入请求头，传递给下游服务
            ServerHttpRequest mutatedRequest = request.mutate()
                    .headers(headers -> {
                        headers.remove(HeaderConstants.HEADER_TENANT_ID);
                        headers.remove(HeaderConstants.HEADER_USER_ID);
                        headers.remove(HeaderConstants.HEADER_USERNAME);
                        headers.remove(HeaderConstants.HEADER_EMAIL);
                        headers.remove(HeaderConstants.HEADER_ACTOR_USER_ID);
                        headers.remove(HeaderConstants.HEADER_ACTOR_TENANT_ID);
                        headers.remove(HeaderConstants.HEADER_IMPERSONATION);
                    })
                    .header(HeaderConstants.HEADER_TENANT_ID, String.valueOf(tenantId))
                    .header(HeaderConstants.HEADER_USER_ID, String.valueOf(userId))
                    .header(HeaderConstants.HEADER_USERNAME, username)
                    .header(HeaderConstants.HEADER_EMAIL, email != null ? email : "")
                    .header(HeaderConstants.HEADER_IMPERSONATION, String.valueOf(impersonation))
                    .build();

            if (impersonation) {
                if (actorUserId != null) {
                    mutatedRequest = mutatedRequest.mutate()
                            .header(HeaderConstants.HEADER_ACTOR_USER_ID, String.valueOf(actorUserId))
                            .build();
                }
                if (actorTenantId != null) {
                    mutatedRequest = mutatedRequest.mutate()
                            .header(HeaderConstants.HEADER_ACTOR_TENANT_ID, String.valueOf(actorTenantId))
                            .build();
                }
            }

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.error("认证异常: {}", e.getMessage());
            return unauthorized(exchange, "Token 验证失败");
        }
    }

    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhitelisted(String path) {
        return whitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern.trim(), path));
    }

    /**
     * 从请求中提取 Token
     * 优先从 Authorization Header 获取，其次从 Cookie 获取
     */
    private String extractToken(ServerHttpRequest request) {
        // 1. 优先从 Authorization Header 获取
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // 2. 从 Cookie 获取（支持 SSE 等无法设置 Header 的场景）
        HttpCookie authCookie = request.getCookies().getFirst(AUTH_COOKIE_NAME);
        if (authCookie != null) {
            return authCookie.getValue();
        }

        return null;
    }

    /**
     * 返回 401 未授权响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"code\":401,\"message\":\"%s\"}", message);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
