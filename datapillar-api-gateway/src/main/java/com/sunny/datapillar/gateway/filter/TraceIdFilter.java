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
 * link tracingIDfilter Responsible for link trackingIDRequest filtering and context control
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Component
public class TraceIdFilter implements GlobalFilter, Ordered {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    // Check if the request already has TraceId（from upstream）
    String traceId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.HEADER_TRACE_ID);
    String requestId =
        exchange.getRequest().getHeaders().getFirst(HeaderConstants.HEADER_REQUEST_ID);

    if (traceId == null || traceId.isEmpty()) {
      // generate new TraceId
      traceId = generateTraceId();
    }
    if (requestId == null || requestId.isEmpty()) {
      // generate new RequestId
      requestId = generateRequestId();
    }

    // will TraceId Inject request header，Passed to downstream services
    String finalTraceId = traceId;
    String finalRequestId = requestId;
    ServerHttpRequest mutatedRequest =
        exchange
            .getRequest()
            .mutate()
            .header(HeaderConstants.HEADER_TRACE_ID, finalTraceId)
            .header(HeaderConstants.HEADER_REQUEST_ID, finalRequestId)
            .build();

    // will TraceId / RequestId Add to response header，Facilitate client tracking
    exchange.getResponse().getHeaders().add(HeaderConstants.HEADER_TRACE_ID, finalTraceId);
    exchange.getResponse().getHeaders().add(HeaderConstants.HEADER_REQUEST_ID, finalRequestId);

    return chain.filter(exchange.mutate().request(mutatedRequest).build());
  }

  /** generate TraceId Format：Timestamp(milliseconds) + random number */
  private String generateTraceId() {
    return String.format(
        "%d-%s", System.currentTimeMillis(), UUID.randomUUID().toString().substring(0, 8));
  }

  /** generate RequestId Format：req-Timestamp(milliseconds)-random number */
  private String generateRequestId() {
    return String.format(
        "req-%d-%s", System.currentTimeMillis(), UUID.randomUUID().toString().substring(0, 8));
  }

  @Override
  public int getOrder() {
    // highest priority，Execute before authentication
    return -200;
  }
}
