package com.sunny.datapillar.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.gateway.response.GatewayErrorCode;
import com.sunny.datapillar.gateway.response.GatewayResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * 网关全局异常处理器
 * 响应式异常处理
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

        // 已经提交响应则跳过
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 获取 TraceId
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");

        // 根据异常类型构建响应
        GatewayResponse<Object> gatewayResponse;
        HttpStatus httpStatus;

        if (ex instanceof ResponseStatusException responseStatusException) {
            // Spring 框架异常
            log.warn("[{}] 响应状态异常: {}", traceId, ex.getMessage());
            httpStatus = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
            gatewayResponse = buildResponseFromStatus(httpStatus, responseStatusException.getReason());

        } else if (ex instanceof ConnectException) {
            // 连接异常 - 下游服务不可用
            log.error("[{}] 服务连接失败: {}", traceId, ex.getMessage());
            gatewayResponse = GatewayResponse.error(GatewayErrorCode.SERVICE_UNAVAILABLE);
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE;

        } else if (ex instanceof TimeoutException) {
            // 超时异常
            log.error("[{}] 服务响应超时: {}", traceId, ex.getMessage());
            gatewayResponse = GatewayResponse.error(GatewayErrorCode.GATEWAY_TIMEOUT);
            httpStatus = HttpStatus.GATEWAY_TIMEOUT;

        } else {
            // 未知异常
            log.error("[{}] 网关内部错误: {}", traceId, ex.getMessage(), ex);
            gatewayResponse = GatewayResponse.error(GatewayErrorCode.GATEWAY_INTERNAL_ERROR);
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return writeResponse(response, httpStatus, gatewayResponse);
    }

    /**
     * 根据 HTTP 状态码构建响应
     */
    private GatewayResponse<Object> buildResponseFromStatus(HttpStatus status, String reason) {
        return switch (status) {
            case NOT_FOUND -> GatewayResponse.error(GatewayErrorCode.RESOURCE_NOT_FOUND);
            case UNAUTHORIZED -> GatewayResponse.error(GatewayErrorCode.UNAUTHORIZED);
            case FORBIDDEN -> GatewayResponse.error(GatewayErrorCode.FORBIDDEN);
            case SERVICE_UNAVAILABLE -> GatewayResponse.error(GatewayErrorCode.SERVICE_UNAVAILABLE);
            case GATEWAY_TIMEOUT -> GatewayResponse.error(GatewayErrorCode.GATEWAY_TIMEOUT);
            default -> GatewayResponse.error(status.name(), reason != null ? reason : status.getReasonPhrase());
        };
    }

    /**
     * 写入响应
     */
    private Mono<Void> writeResponse(ServerHttpResponse response, HttpStatus status, GatewayResponse<Object> gatewayResponse) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(gatewayResponse);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            byte[] fallback = "{\"code\":\"INTERNAL_ERROR\",\"message\":\"响应序列化失败\"}".getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(fallback)));
        }
    }
}
