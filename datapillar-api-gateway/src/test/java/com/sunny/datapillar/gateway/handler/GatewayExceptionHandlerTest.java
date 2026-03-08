package com.sunny.datapillar.gateway.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayExceptionHandlerTest {

  @Test
  void shouldIncludeTraceIdFromResponseHeaderInErrorBody() {
    GatewayExceptionHandler handler = new GatewayExceptionHandler(new ObjectMapper());
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/studio/jobs").build());
    exchange.getResponse().getHeaders().set(HeaderConstants.HEADER_TRACE_ID, "trace-123");

    handler.handle(exchange, new GatewayUnauthorizedException("Invalid token")).block();

    assertEquals(401, exchange.getResponse().getStatusCode().value());
    String body = exchange.getResponse().getBodyAsString().block();
    assertTrue(body.contains("\"code\":401"));
    assertTrue(body.contains("\"type\":\"UNAUTHORIZED\""));
    assertTrue(body.contains("\"message\":\"Invalid token\""));
    assertTrue(body.contains("\"traceId\":\"trace-123\""));
  }
}
