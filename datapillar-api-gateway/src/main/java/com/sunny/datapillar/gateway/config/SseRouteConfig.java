package com.sunny.datapillar.gateway.config;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSE route configuration. Responsible for SSE route assembly and bean initialization.
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

  /** Resolve SSE target route from gateway route definitions. */
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
      log.warn("ai-service route not found; skip SSE dedicated route");
      return builder.routes().build();
    }

    return builder
        .routes()
        // AI SSE service - ETL workflow
        .route(
            SSE_ROUTE_ID,
            r ->
                r.order(0)
                    .path(SSE_BIZ_PATH)
                    .filters(
                        f -> {
                          return f.addResponseHeader("X-Accel-Buffering", "no")
                              .addResponseHeader("Cache-Control", "no-cache");
                        })
                    .uri(targetUri.get()))
        .build();
  }
}
