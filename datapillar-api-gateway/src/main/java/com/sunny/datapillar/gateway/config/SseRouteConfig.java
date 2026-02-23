package com.sunny.datapillar.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

/**
 * SseRoute配置
 * 负责SseRoute配置装配与Bean初始化
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SseRouteConfig {

    private static final String AI_SERVICE_ROUTE_ID = "ai-service";
    private static final String SSE_ROUTE_ID = "ai-sse-etl";
    private static final String SSE_BIZ_PATH = "/api/ai/biz/etl/workflow/sse";

    private final GatewayProperties gatewayProperties;

    /**
     * 从路由配置中解析 SSE 目标路由。
     */
    private Optional<String> resolveSseTargetUri() {
        List<RouteDefinition> routes = gatewayProperties.getRoutes();
        if (routes == null || routes.isEmpty()) {
            return Optional.empty();
        }

        return routes.stream()
                .filter(route -> AI_SERVICE_ROUTE_ID.equals(route.getId()))
                .map(route -> route.getUri().toString())
                .findFirst()
                .filter(uri -> !uri.isBlank());
    }

    @Bean
    public RouteLocator sseRoutes(RouteLocatorBuilder builder) {
        Optional<String> targetUri = resolveSseTargetUri();
        if (targetUri.isEmpty()) {
            log.warn("未找到 ai-service 路由配置，跳过 SSE 专用路由");
            return builder.routes().build();
        }

        return builder.routes()
                // AI SSE 服务 - ETL 工作流
                .route(SSE_ROUTE_ID, r -> r
                        .order(0)
                        .path(SSE_BIZ_PATH)
                        .filters(f -> {
                            return f
                                    .addResponseHeader("X-Accel-Buffering", "no")
                                    .addResponseHeader("Cache-Control", "no-cache");
                        })
                        .uri(targetUri.get())
                )
                .build();
    }
}
