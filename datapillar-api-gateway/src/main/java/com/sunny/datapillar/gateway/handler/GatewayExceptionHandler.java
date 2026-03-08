package com.sunny.datapillar.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.exception.ExceptionMapper;
import com.sunny.datapillar.common.response.ErrorResponse;
import com.sunny.datapillar.gateway.filter.TraceIdFilter;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Gateway exception handler responsible for unified error output.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Order(-1)
@Component
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

  private final ObjectMapper objectMapper;

  public GatewayExceptionHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.error(ex);
    }

    DatapillarRuntimeException mappedException = mapException(ex);
    ExceptionMapper.ExceptionDetail detail = ExceptionMapper.resolve(mappedException);
    String traceId = resolveTraceId(exchange, detail.traceId());

    if (detail.serverError()) {
      log.error("Gateway exception: type={}, message={}", detail.type(), detail.message(), ex);
    } else {
      log.warn(
          "Gateway request exception: type={}, message={}", detail.type(), detail.message(), ex);
    }

    ErrorResponse body =
        ErrorResponse.of(detail.errorCode(), detail.type(), detail.message(), traceId);
    return writeResponse(exchange, response, HttpStatus.valueOf(detail.httpStatus()), body);
  }

  private DatapillarRuntimeException mapException(Throwable ex) {
    if (ex instanceof DatapillarRuntimeException runtimeException) {
      return runtimeException;
    }

    if (ex instanceof ResponseStatusException responseStatusException) {
      HttpStatus status = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
      String reason =
          responseStatusException.getReason() == null
              ? status.getReasonPhrase()
              : responseStatusException.getReason();
      return switch (status) {
        case BAD_REQUEST ->
            new com.sunny.datapillar.gateway.exception.base.GatewayBadRequestException(ex, reason);
        case UNAUTHORIZED ->
            new com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException(
                ex, reason);
        case FORBIDDEN ->
            new com.sunny.datapillar.gateway.exception.base.GatewayForbiddenException(ex, reason);
        case NOT_FOUND ->
            new com.sunny.datapillar.gateway.exception.base.GatewayNotFoundException(ex, reason);
        case SERVICE_UNAVAILABLE ->
            new com.sunny.datapillar.gateway.exception.base.GatewayServiceUnavailableException(
                ex, reason);
        default ->
            new com.sunny.datapillar.gateway.exception.base.GatewayInternalException(ex, reason);
      };
    }

    if (ex instanceof ConnectException) {
      return new com.sunny.datapillar.gateway.exception.base.GatewayServiceUnavailableException(
          ex, "Service connection failed");
    }

    if (ex instanceof TimeoutException) {
      return new com.sunny.datapillar.gateway.exception.base.GatewayServiceUnavailableException(
          ex, "Service response timeout");
    }

    return new com.sunny.datapillar.gateway.exception.base.GatewayInternalException(
        ex, "Gateway internal error");
  }

  private Mono<Void> writeResponse(
      ServerWebExchange exchange,
      ServerHttpResponse response,
      HttpStatus status,
      ErrorResponse body) {
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

    try {
      byte[] bytes = objectMapper.writeValueAsBytes(body);
      return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    } catch (JsonProcessingException e) {
      log.error("Gateway response serialization failed", e);
      String traceId = resolveTraceId(exchange, ExceptionMapper.resolve(e).traceId());
      String fallback =
          String.format(
              "{\"code\":500,\"message\":\"Response serialization failed\",\"type\":\"INTERNAL_ERROR\",\"traceId\":\"%s\"}",
              escapeJson(traceId));
      return response.writeWith(
          Mono.just(response.bufferFactory().wrap(fallback.getBytes(StandardCharsets.UTF_8))));
    }
  }

  private String resolveTraceId(ServerWebExchange exchange, String fallbackTraceId) {
    if (exchange == null) {
      return trimToNull(fallbackTraceId);
    }
    String responseTraceId =
        trimToNull(exchange.getResponse().getHeaders().getFirst(HeaderConstants.HEADER_TRACE_ID));
    if (responseTraceId != null) {
      return responseTraceId;
    }
    String requestTraceId =
        trimToNull(exchange.getRequest().getHeaders().getFirst(HeaderConstants.HEADER_TRACE_ID));
    if (requestTraceId != null) {
      return requestTraceId;
    }
    Object attributeTraceId = exchange.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
    if (attributeTraceId instanceof String traceId && StringUtils.hasText(traceId)) {
      return traceId.trim();
    }
    return trimToNull(fallbackTraceId);
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }
}
