package com.sunny.datapillar.gateway.filter;

import com.sunny.datapillar.common.constant.HeaderConstants;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Trace-id request filter for gateway request and response propagation.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

  public static final String TRACE_ID_ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";
  public static final String REQUEST_ID_ATTRIBUTE = TraceIdFilter.class.getName() + ".requestId";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String traceId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.HEADER_TRACE_ID);
    String requestId =
        exchange.getRequest().getHeaders().getFirst(HeaderConstants.HEADER_REQUEST_ID);

    if (traceId == null || traceId.isEmpty()) {
      traceId = generateTraceId();
    }
    if (requestId == null || requestId.isEmpty()) {
      requestId = generateRequestId();
    }

    String finalTraceId = traceId;
    String finalRequestId = requestId;
    exchange.getAttributes().put(TRACE_ID_ATTRIBUTE, finalTraceId);
    exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, finalRequestId);

    ServerHttpRequest mutatedRequest =
        exchange
            .getRequest()
            .mutate()
            .header(HeaderConstants.HEADER_TRACE_ID, finalTraceId)
            .header(HeaderConstants.HEADER_REQUEST_ID, finalRequestId)
            .build();

    exchange.getResponse().getHeaders().set(HeaderConstants.HEADER_TRACE_ID, finalTraceId);
    exchange.getResponse().getHeaders().set(HeaderConstants.HEADER_REQUEST_ID, finalRequestId);

    return chain.filter(exchange.mutate().request(mutatedRequest).build());
  }

  private String generateTraceId() {
    return String.format(
        "%d-%s", System.currentTimeMillis(), UUID.randomUUID().toString().substring(0, 8));
  }

  private String generateRequestId() {
    return String.format(
        "req-%d-%s", System.currentTimeMillis(), UUID.randomUUID().toString().substring(0, 8));
  }

  @Override
  public int getOrder() {
    return -200;
  }
}
