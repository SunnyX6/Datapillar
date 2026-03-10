package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.common.response.ErrorResponse;
import com.sunny.datapillar.gateway.config.AuthenticationProperties;
import com.sunny.datapillar.gateway.exception.base.GatewayBadRequestException;
import com.sunny.datapillar.gateway.exception.base.GatewayForbiddenException;
import com.sunny.datapillar.gateway.exception.base.GatewayServiceUnavailableException;
import com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Client that resolves authoritative API key principal context from auth service. */
@Slf4j
@Component
public class ApiKeyAuthenticationContextClient {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);
  private static final ParameterizedTypeReference<ApiResponse<AuthAuthenticationContext>>
      RESPONSE_TYPE = new ParameterizedTypeReference<>() {};

  private final AuthenticationProperties properties;
  private final WebClient webClient;

  public ApiKeyAuthenticationContextClient(
      AuthenticationProperties properties, WebClient.Builder webClientBuilder) {
    this.properties = properties;
    this.webClient = webClientBuilder.build();
    this.properties.validate();
  }

  public Mono<AuthAuthenticationContext> resolve(String apiKey, String clientIp, String traceId) {
    String normalizedApiKey = trimToNull(apiKey);
    if (normalizedApiKey == null) {
      return Mono.error(new GatewayUnauthorizedException("Missing authentication information"));
    }

    return webClient
        .post()
        .uri(properties.issuerApiKeyResolveUri())
        .headers(
            headers -> {
              headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
              headers.setContentType(MediaType.APPLICATION_JSON);
              if (StringUtils.hasText(traceId)) {
                headers.set(HeaderConstants.HEADER_TRACE_ID, traceId.trim());
              }
            })
        .bodyValue(new ResolveRequest(normalizedApiKey, trimToNull(clientIp), trimToNull(traceId)))
        .exchangeToMono(this::readResponse)
        .timeout(REQUEST_TIMEOUT)
        .onErrorMap(this::mapTransportError);
  }

  private Mono<AuthAuthenticationContext> readResponse(ClientResponse response) {
    HttpStatus status = HttpStatus.valueOf(response.statusCode().value());
    if (status.is2xxSuccessful()) {
      return response.bodyToMono(RESPONSE_TYPE).map(this::unwrapResponse);
    }
    return response
        .bodyToMono(ErrorResponse.class)
        .defaultIfEmpty(new ErrorResponse(status.value(), null, status.getReasonPhrase(), null))
        .flatMap(errorResponse -> Mono.error(mapStatus(status, errorResponse)));
  }

  private AuthAuthenticationContext unwrapResponse(
      ApiResponse<AuthAuthenticationContext> response) {
    if (response == null || response.getCode() != 0 || response.getData() == null) {
      throw new GatewayServiceUnavailableException("Invalid API key context response");
    }
    return response.getData();
  }

  private RuntimeException mapStatus(HttpStatus status, ErrorResponse errorResponse) {
    String message = normalizeMessage(status, errorResponse);
    return switch (status) {
      case BAD_REQUEST -> new GatewayBadRequestException(message);
      case UNAUTHORIZED -> new GatewayUnauthorizedException(message);
      case FORBIDDEN -> new GatewayForbiddenException(message);
      default -> new GatewayServiceUnavailableException(message);
    };
  }

  private Throwable mapTransportError(Throwable throwable) {
    if (throwable instanceof DatapillarRuntimeException) {
      return throwable;
    }
    log.error(
        "security_event event=api_key_context_resolve_failed issuer={} reason={}",
        properties.getIssuer(),
        throwable.getMessage(),
        throwable);
    return new GatewayServiceUnavailableException(
        throwable, "Failed to resolve API key authentication context");
  }

  private String normalizeMessage(HttpStatus status, ErrorResponse errorResponse) {
    String message = errorResponse == null ? null : trimToNull(errorResponse.getMessage());
    if (message != null) {
      return message;
    }
    return switch (status) {
      case BAD_REQUEST -> "Bad API key context request";
      case UNAUTHORIZED -> "Invalid API key";
      case FORBIDDEN -> "Access denied";
      default -> "Failed to resolve API key authentication context";
    };
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private record ResolveRequest(String apiKey, String clientIp, String traceId) {}
}
