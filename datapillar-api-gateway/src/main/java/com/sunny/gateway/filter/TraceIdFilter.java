package com.sunny.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 链路追踪过滤器
 * 为每个请求生成唯一的 TraceId，用于日志追踪和问题排查
 *
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
@Slf4j
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查请求中是否已有 TraceId（来自上游）
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);

        if (traceId == null || traceId.isEmpty()) {
            // 生成新的 TraceId
            traceId = generateTraceId();
        }

        // 将 TraceId 注入请求头，传递给下游服务
        String finalTraceId = traceId;
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        // 将 TraceId 添加到响应头，方便客户端追踪
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * 生成 TraceId
     * 格式：时间戳(毫秒) + 随机数
     */
    private String generateTraceId() {
        return String.format("%d-%s",
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public int getOrder() {
        // 最高优先级，在认证之前执行
        return -200;
    }
}
