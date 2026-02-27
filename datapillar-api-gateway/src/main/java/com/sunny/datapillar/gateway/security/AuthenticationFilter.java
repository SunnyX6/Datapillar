package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.rpc.security.v1.AuthenticationService;
import com.sunny.datapillar.common.rpc.security.v1.CheckAuthenticationRequest;
import com.sunny.datapillar.common.rpc.security.v1.CheckAuthenticationResponse;
import com.sunny.datapillar.common.rpc.security.v1.DenyCode;
import com.sunny.datapillar.common.rpc.security.v1.Principal;
import com.sunny.datapillar.common.rpc.security.v1.RpcMeta;
import com.sunny.datapillar.gateway.config.AuthenticationProperties;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
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
import reactor.core.scheduler.Schedulers;

/**
 * 网关认证过滤器
 * 负责网关统一认证判定与主体上下文注入
 *
 * @author Sunny
 * @date 2026-02-19
 */
@Slf4j
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    @DubboReference(
            interfaceClass = AuthenticationService.class,
            version = "${datapillar.rpc.version:1.0.0}",
            group = "${datapillar.rpc.group:datapillar}",
            check = false,
            timeout = 3000
    )
    private AuthenticationService authenticationService;

    private final AuthenticationProperties properties;
    private final ClientIpResolver clientIpResolver;

    public AuthenticationFilter(AuthenticationProperties properties, ClientIpResolver clientIpResolver) {
        this.properties = properties;
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
        if (isPublicPath(path) || !isProtectedPath(path)) {
            return chain.filter(exchange);
        }

        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            return Mono.error(new com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException("缺少认证信息"));
        }

        CheckAuthenticationRequest checkRequest = buildAuthenticationRequest(exchange, token, path);
        return Mono.fromCallable(() -> authenticationService.checkAuthentication(checkRequest))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(response -> handleAuthenticationResult(exchange, chain, response));
    }

    private Mono<Void> handleAuthenticationResult(ServerWebExchange exchange,
                                                  GatewayFilterChain chain,
                                                  CheckAuthenticationResponse response) {
        if (response == null) {
            return Mono.error(new com.sunny.datapillar.gateway.exception.base.GatewayServiceUnavailableException("认证服务无响应"));
        }

        if (!response.getAuthenticated()) {
            String message = StringUtils.hasText(response.getMessage()) ? response.getMessage() : "认证失败";
            if (isForbidden(response.getDenyCode())) {
                return Mono.error(new com.sunny.datapillar.gateway.exception.base.GatewayForbiddenException(message));
            }
            return Mono.error(new com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException(message));
        }

        Principal principal = response.getPrincipal();
        if (principal == null || principal.getUserId() <= 0 || principal.getTenantId() <= 0) {
            return Mono.error(new com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException("认证主体缺失"));
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().headers(headers -> {
            sanitizeContextHeaders(headers);
            headers.set(HeaderConstants.HEADER_USER_ID, String.valueOf(principal.getUserId()));
            headers.set(HeaderConstants.HEADER_TENANT_ID, String.valueOf(principal.getTenantId()));
            if (StringUtils.hasText(principal.getTenantCode())) {
                headers.set(HeaderConstants.HEADER_TENANT_CODE, principal.getTenantCode());
            }
            if (StringUtils.hasText(principal.getUsername())) {
                headers.set(HeaderConstants.HEADER_USERNAME, principal.getUsername());
            }
            if (StringUtils.hasText(principal.getEmail())) {
                headers.set(HeaderConstants.HEADER_EMAIL, principal.getEmail());
            }
            if (principal.getActorUserId() > 0) {
                headers.set(HeaderConstants.HEADER_ACTOR_USER_ID, String.valueOf(principal.getActorUserId()));
            }
            if (principal.getActorTenantId() > 0) {
                headers.set(HeaderConstants.HEADER_ACTOR_TENANT_ID, String.valueOf(principal.getActorTenantId()));
            }
            headers.set(HeaderConstants.HEADER_IMPERSONATION,
                    String.valueOf(principal.getImpersonation()));
            if (StringUtils.hasText(response.getGatewayAssertion())) {
                headers.set(HeaderConstants.HEADER_GATEWAY_ASSERTION, response.getGatewayAssertion());
            }
        }).build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private CheckAuthenticationRequest buildAuthenticationRequest(ServerWebExchange exchange,
                                                                  String token,
                                                                  String path) {
        ServerHttpRequest request = exchange.getRequest();
        String tenantHeader = request.getHeaders().getFirst(HeaderConstants.HEADER_TENANT_ID);
        Long tenantHint = parsePositiveLong(tenantHeader);

        RpcMeta.Builder metaBuilder = RpcMeta.newBuilder()
                .setProtocolVersion(properties.getProtocolVersion())
                .setCallerService("datapillar-api-gateway");
        String traceId = request.getHeaders().getFirst(HeaderConstants.HEADER_TRACE_ID);
        if (StringUtils.hasText(traceId)) {
            metaBuilder.setTraceId(traceId);
        }
        String requestId = request.getHeaders().getFirst(HeaderConstants.HEADER_REQUEST_ID);
        if (StringUtils.hasText(requestId)) {
            metaBuilder.setRequestId(requestId);
        }
        String clientIp = clientIpResolver.resolve(request);
        if (StringUtils.hasText(clientIp)) {
            metaBuilder.setClientIp(clientIp);
        }

        String method = request.getMethod() == null ? "GET" : request.getMethod().name().toUpperCase(Locale.ROOT);
        CheckAuthenticationRequest.Builder requestBuilder = CheckAuthenticationRequest.newBuilder()
                .setMeta(metaBuilder.build())
                .setToken(token)
                .setMethod(method)
                .setPath(path);
        if (tenantHint != null) {
            requestBuilder.setTenantIdHint(tenantHint);
        }
        return requestBuilder.build();
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

    private boolean isForbidden(DenyCode denyCode) {
        return denyCode == DenyCode.PERMISSION_DENIED
                || denyCode == DenyCode.TENANT_DISABLED
                || denyCode == DenyCode.USER_DISABLED;
    }

    private Long parsePositiveLong(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            long value = Long.parseLong(text.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void sanitizeContextHeaders(HttpHeaders headers) {
        headers.remove(HeaderConstants.HEADER_USER_ID);
        headers.remove(HeaderConstants.HEADER_TENANT_ID);
        headers.remove(HeaderConstants.HEADER_TENANT_CODE);
        headers.remove(HeaderConstants.HEADER_USERNAME);
        headers.remove(HeaderConstants.HEADER_EMAIL);
        headers.remove(HeaderConstants.HEADER_ACTOR_USER_ID);
        headers.remove(HeaderConstants.HEADER_ACTOR_TENANT_ID);
        headers.remove(HeaderConstants.HEADER_IMPERSONATION);
        headers.remove(HeaderConstants.HEADER_GATEWAY_ASSERTION);
    }

    @Override
    public int getOrder() {
        return -120;
    }
}
