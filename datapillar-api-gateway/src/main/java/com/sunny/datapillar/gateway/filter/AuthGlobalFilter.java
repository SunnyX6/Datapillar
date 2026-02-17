package com.sunny.datapillar.gateway.filter;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.utils.ExceptionMessageUtil;
import com.sunny.datapillar.gateway.config.GatewayAssertionProperties;
import com.sunny.datapillar.gateway.config.GatewayAuthProperties;
import com.sunny.datapillar.gateway.security.ClientIpResolver;
import com.sunny.datapillar.gateway.security.GatewayAssertionSigner;
import com.sunny.datapillar.gateway.security.SessionStateVerifier;
import com.sunny.datapillar.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
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
 * 认证全局过滤器
 * 负责认证全局请求过滤与上下文控制
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final GatewayAssertionSigner assertionSigner;
    private final GatewayAssertionProperties assertionProperties;
    private final SessionStateVerifier sessionStateVerifier;
    private final ClientIpResolver clientIpResolver;
    private final List<String> whitelist;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthGlobalFilter(JwtUtil jwtUtil,
                            GatewayAssertionSigner assertionSigner,
                            GatewayAssertionProperties assertionProperties,
                            SessionStateVerifier sessionStateVerifier,
                            ClientIpResolver clientIpResolver,
                            GatewayAuthProperties authProperties) {
        this.jwtUtil = jwtUtil;
        this.assertionSigner = assertionSigner;
        this.assertionProperties = assertionProperties;
        this.sessionStateVerifier = sessionStateVerifier;
        this.clientIpResolver = clientIpResolver;
        this.whitelist = resolveWhitelist(authProperties);
    }

    private List<String> resolveWhitelist(GatewayAuthProperties authProperties) {
        if (authProperties == null || authProperties.getWhitelist() == null) {
            throw new IllegalStateException("gateway.auth.whitelist 未配置");
        }
        List<String> configured = authProperties.getWhitelist().stream()
                .filter(path -> path != null && !path.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (configured.isEmpty()) {
            throw new IllegalStateException("gateway.auth.whitelist 未配置");
        }
        return configured;
    }

    /**
     * Cookie 名称常量
     */
    private static final String AUTH_COOKIE_NAME = "auth-token";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        ServerHttpRequest normalizedClientRequest = normalizeClientIpHeaders(request);

        log.info("AuthFilter processing path: {}, whitelist: {}", path, whitelist);

        // 白名单路径直接放行
        if (isWhitelisted(path)) {
            log.info("Path {} is whitelisted, passing through", path);
            return chain.filter(exchange.mutate().request(normalizedClientRequest).build());
        }

        // 提取 Token：优先从 Authorization Header 获取，其次从 Cookie 获取
        String token = extractToken(normalizedClientRequest);
        if (token == null) {
            log.warn("认证失败 - 缺少认证信息: {}", path);
            return unauthorized(exchange, "缺少认证信息");
        }

        try {
            Claims claims = jwtUtil.parseToken(token);

            // 检查是否是 access token
            String tokenType = jwtUtil.getTokenType(claims);
            if (!"access".equals(tokenType)) {
                log.warn("认证失败 - Token 类型错误: {}", tokenType);
                return unauthorized(exchange, "Token 类型错误");
            }

            // 提取用户信息
            Long userId = jwtUtil.getUserId(claims);
            String username = jwtUtil.getUsername(claims);
            String email = jwtUtil.getEmail(claims);
            Long tenantId = jwtUtil.getTenantId(claims);
            if (userId == null) {
                log.warn("认证失败 - Token 缺少用户信息: {}", path);
                return unauthorized(exchange, "Token 缺少用户信息");
            }
            if (tenantId == null) {
                log.warn("认证失败 - Token 缺少租户信息: {}", path);
                return unauthorized(exchange, "Token 缺少租户信息");
            }

            Long actorUserId = jwtUtil.getActorUserId(claims);
            Long actorTenantId = jwtUtil.getActorTenantId(claims);
            boolean impersonation = jwtUtil.isImpersonation(claims);
            List<String> roles = jwtUtil.getRoles(claims);
            String sid = jwtUtil.getSessionId(claims);
            String accessJti = jwtUtil.getTokenId(claims);
            if (sid == null || sid.isBlank() || accessJti == null || accessJti.isBlank()) {
                log.warn("认证失败 - Token 缺少会话标识: {}", path);
                return unauthorized(exchange, "Token 无效");
            }

            if (!assertionProperties.isEnabled()) {
                log.error("网关断言未启用，拒绝受保护请求: {}", path);
                return unauthorized(exchange, "内部断言未启用");
            }

            String requestMethod = request.getMethod() != null ? request.getMethod().name() : "";

            return sessionStateVerifier.isAccessTokenActive(sid, accessJti)
                    .flatMap(active -> {
                        if (!active) {
                            log.warn("认证失败 - Token 已吊销: sid={}, jti={}, path={}", sid, accessJti, path);
                            return unauthorized(exchange, "Token 已失效");
                        }

                        String assertion = assertionSigner.sign(new GatewayAssertionSigner.AssertionPayload(
                                userId,
                                tenantId,
                                username,
                                email,
                                roles,
                                impersonation,
                                actorUserId,
                                actorTenantId,
                                requestMethod,
                                path
                        ));

                        if (assertion == null || assertion.isBlank()) {
                            log.error("网关断言签发失败: path={}", path);
                            return unauthorized(exchange, "内部断言签发失败");
                        }

                        ServerHttpRequest.Builder requestBuilder = normalizedClientRequest.mutate()
                                .headers(headers -> {
                                    headers.remove(HeaderConstants.HEADER_TENANT_ID);
                                    headers.remove(HeaderConstants.HEADER_USER_ID);
                                    headers.remove(HeaderConstants.HEADER_USERNAME);
                                    headers.remove(HeaderConstants.HEADER_EMAIL);
                                    headers.remove(HeaderConstants.HEADER_ACTOR_USER_ID);
                                    headers.remove(HeaderConstants.HEADER_ACTOR_TENANT_ID);
                                    headers.remove(HeaderConstants.HEADER_IMPERSONATION);
                                    headers.remove(assertionProperties.getHeaderName());
                                });
                        requestBuilder.header(assertionProperties.getHeaderName(), assertion);
                        ServerHttpRequest mutatedRequest = requestBuilder.build();
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    })
                    .onErrorResume(ex -> {
                        log.error("会话状态校验异常: {}", ex.getMessage(), ex);
                        String message = ExceptionMessageUtil.compose("Token 状态校验失败", ex);
                        return unauthorized(exchange, message);
                    });

        } catch (DatapillarRuntimeException e) {
            log.warn("认证失败 - Token 无效或已过期: {}", path, e);
            String message = ExceptionMessageUtil.compose("Token 无效或已过期", e);
            return unauthorized(exchange, message);
        } catch (Exception e) {
            log.error("认证异常: {}", e.getMessage(), e);
            String message = ExceptionMessageUtil.compose("Token 验证失败", e);
            return unauthorized(exchange, message);
        }
    }

    /**
     * 检查路径是否在白名单中
     */
    private boolean isWhitelisted(String path) {
        return whitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private ServerHttpRequest normalizeClientIpHeaders(ServerHttpRequest request) {
        String clientIp = clientIpResolver.resolve(request);
        ServerHttpRequest.Builder builder = request.mutate().headers(headers -> {
            headers.remove("X-Forwarded-For");
            headers.remove("X-Real-IP");
        });
        if (clientIp != null && !clientIp.isBlank() && !"unknown".equalsIgnoreCase(clientIp)) {
            builder.header("X-Forwarded-For", clientIp);
            builder.header("X-Real-IP", clientIp);
        }
        return builder.build();
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

        String body = String.format("{\"code\":401,\"message\":\"%s\"}", escapeJson(message));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
