package com.sunny.datapillar.gateway.filter;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.gateway.security.ClientIpResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Logging过滤器
 * 负责Logging请求过滤与上下文控制
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private final ClientIpResolver clientIpResolver;

    public LoggingFilter(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();

        String traceId = request.getHeaders().getFirst(HeaderConstants.HEADER_TRACE_ID);
        String method = request.getMethod().name();
        String path = request.getPath().value();
        String clientIp = getClientIp(request);

        // 请求日志
        log.info("[{}] --> {} {} from {}", traceId, method, path, clientIp);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 0;

            // 响应日志
            if (statusCode >= 400) {
                log.warn("[{}] <-- {} {} {}ms", traceId, statusCode, path, duration);
            } else {
                log.info("[{}] <-- {} {} {}ms", traceId, statusCode, path, duration);
            }
        }));
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(ServerHttpRequest request) {
        String ip = clientIpResolver.resolve(request);
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }

    @Override
    public int getOrder() {
        // 在 TraceId 之后，认证之前
        return -150;
    }
}
