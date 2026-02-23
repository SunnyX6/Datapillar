package com.sunny.datapillar.gateway.filter;

import com.sunny.datapillar.common.constant.HeaderConstants;
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
 * 链路追踪ID过滤器
 * 负责链路追踪ID请求过滤与上下文控制
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 检查请求中是否已有 TraceId（来自上游）
        String traceId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.HEADER_TRACE_ID);
        String requestId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.HEADER_REQUEST_ID);

        if (traceId == null || traceId.isEmpty()) {
            // 生成新的 TraceId
            traceId = generateTraceId();
        }
        if (requestId == null || requestId.isEmpty()) {
            // 生成新的 RequestId
            requestId = generateRequestId();
        }

        // 将 TraceId 注入请求头，传递给下游服务
        String finalTraceId = traceId;
        String finalRequestId = requestId;
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(HeaderConstants.HEADER_TRACE_ID, finalTraceId)
                .header(HeaderConstants.HEADER_REQUEST_ID, finalRequestId)
                .build();

        // 将 TraceId / RequestId 添加到响应头，方便客户端追踪
        exchange.getResponse().getHeaders().add(HeaderConstants.HEADER_TRACE_ID, finalTraceId);
        exchange.getResponse().getHeaders().add(HeaderConstants.HEADER_REQUEST_ID, finalRequestId);

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

    /**
     * 生成 RequestId
     * 格式：req-时间戳(毫秒)-随机数
     */
    private String generateRequestId() {
        return String.format("req-%d-%s",
                System.currentTimeMillis(),
                UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public int getOrder() {
        // 最高优先级，在认证之前执行
        return -200;
    }
}
