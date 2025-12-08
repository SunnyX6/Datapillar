package com.sunny.gateway.filter;

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
 * 请求日志过滤器
 * 记录请求和响应信息，便于问题排查和性能分析
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long startTime = System.currentTimeMillis();

        String traceId = request.getHeaders().getFirst(TraceIdFilter.TRACE_ID_HEADER);
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
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddress() != null ?
                    request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    @Override
    public int getOrder() {
        // 在 TraceId 之后，认证之前
        return -150;
    }
}
