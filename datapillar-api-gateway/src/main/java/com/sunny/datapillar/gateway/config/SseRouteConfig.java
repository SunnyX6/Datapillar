package com.sunny.datapillar.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSE 路由配置
 *
 * 使用 Java DSL 定义 SSE 路由，避免 default-filters（如限流器）干扰流式响应。
 * Java DSL 定义的路由不会自动应用 default-filters。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SseRouteConfig {

    private final GatewayProperties gatewayProperties;

    /**
     * 从 YAML 配置中获取 ai-service 路由的 URI
     */
    private String getAiServiceUri() {
        var routes = gatewayProperties.getRoutes();

        return routes.stream()
            .filter(route -> "ai-service".equals(route.getId()))
            .findFirst()
            .map(route -> route.getUri().toString())
            .orElseThrow(() -> new IllegalStateException("未找到 ai-service 路由配置"));
    }

    @Bean
    public RouteLocator sseRoutes(RouteLocatorBuilder builder) {
        String aiServiceUri = getAiServiceUri();

        return builder.routes()
            // AI SSE 服务 - ETL 工作流
            .route("ai-sse-etl", r -> r
                .order(0)
                .path("/api/ai/etl/workflow/sse")
                .filters(f -> f
                    .addResponseHeader("X-Accel-Buffering", "no")
                    .addResponseHeader("Cache-Control", "no-cache")
                )
                .uri(aiServiceUri)
            )
            .build();
    }
}
