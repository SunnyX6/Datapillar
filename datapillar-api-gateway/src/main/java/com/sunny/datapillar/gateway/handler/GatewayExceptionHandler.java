package com.sunny.datapillar.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.exception.ExceptionMapper;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.InternalException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.response.ErrorResponse;
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
 * 网关异常处理器
 * 负责网关异常处理流程与结果输出
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

        if (detail.serverError()) {
            log.error("网关异常: type={}, message={}", detail.type(), detail.message(), ex);
        } else {
            log.warn("网关请求异常: type={}, message={}", detail.type(), detail.message(), ex);
        }

        ErrorResponse body = ErrorResponse.of(
                detail.errorCode(),
                detail.type(),
                detail.message(),
                detail.traceId());
        return writeResponse(response, HttpStatus.valueOf(detail.httpStatus()), body);
    }

    private DatapillarRuntimeException mapException(Throwable ex) {
        if (ex instanceof DatapillarRuntimeException runtimeException) {
            return runtimeException;
        }

        if (ex instanceof ResponseStatusException responseStatusException) {
            HttpStatus status = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
            String reason = responseStatusException.getReason() == null
                    ? status.getReasonPhrase()
                    : responseStatusException.getReason();
            return switch (status) {
                case BAD_REQUEST -> new BadRequestException(ex, reason);
                case UNAUTHORIZED -> new UnauthorizedException(ex, reason);
                case FORBIDDEN -> new ForbiddenException(ex, reason);
                case NOT_FOUND -> new NotFoundException(ex, reason);
                case SERVICE_UNAVAILABLE -> new ServiceUnavailableException(ex, reason);
                default -> new InternalException(ex, reason);
            };
        }

        if (ex instanceof ConnectException) {
            return new ServiceUnavailableException(ex, "服务连接失败");
        }

        if (ex instanceof TimeoutException) {
            return new ServiceUnavailableException(ex, "服务响应超时");
        }

        return new InternalException(ex, "网关内部错误");
    }

    private Mono<Void> writeResponse(ServerHttpResponse response,
                                     HttpStatus status,
                                     ErrorResponse body) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException e) {
            log.error("网关响应序列化失败", e);
            String traceId = ExceptionMapper.resolve(e).traceId();
            String fallback = String.format(
                    "{\"code\":500,\"message\":\"响应序列化失败\",\"type\":\"INTERNAL_ERROR\",\"traceId\":\"%s\"}",
                    escapeJson(traceId));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(fallback.getBytes(StandardCharsets.UTF_8))));
        }
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
