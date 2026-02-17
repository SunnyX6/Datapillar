package com.sunny.datapillar.gateway.filter;

import com.sunny.datapillar.gateway.config.GatewaySecurityProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
/**
 * HttpsEnforcement过滤器
 * 负责HttpsEnforcement请求过滤与上下文控制
 *
 * @author Sunny
 * @date 2026-01-01
 */

@Component
public class HttpsEnforcementFilter implements GlobalFilter, Ordered {

    private final GatewaySecurityProperties securityProperties;

    public HttpsEnforcementFilter(GatewaySecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!securityProperties.isRequireHttps()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String scheme = request.getURI().getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            return reject(exchange, "仅允许 HTTPS 访问");
        }
        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        byte[] bytes = String.format("{\"code\":403,\"message\":\"%s\"}", message).getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        return -180;
    }
}
