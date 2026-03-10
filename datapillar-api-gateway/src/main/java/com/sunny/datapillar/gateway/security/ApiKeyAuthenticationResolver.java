package com.sunny.datapillar.gateway.security;

import com.sunny.datapillar.common.security.PrincipalType;
import com.sunny.datapillar.gateway.config.AuthenticationProperties;
import com.sunny.datapillar.gateway.exception.base.GatewayUnauthorizedException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/** Resolver for API key authentication on `/openapi/**` routes. */
@Component
public class ApiKeyAuthenticationResolver {

  private final AuthenticationProperties properties;
  private final ApiKeyAuthenticationContextClient authenticationContextClient;

  public ApiKeyAuthenticationResolver(
      AuthenticationProperties properties,
      ApiKeyAuthenticationContextClient authenticationContextClient) {
    this.properties = properties;
    this.authenticationContextClient = authenticationContextClient;
  }

  public Mono<VerifiedAccessToken> resolve(String apiKey, String clientIp, String traceId) {
    String normalizedApiKey = trimToNull(apiKey);
    if (normalizedApiKey == null) {
      return Mono.error(new GatewayUnauthorizedException("Missing authentication information"));
    }
    return authenticationContextClient
        .resolve(normalizedApiKey, clientIp, traceId)
        .map(this::buildVerifiedPrincipal);
  }

  private VerifiedAccessToken buildVerifiedPrincipal(AuthAuthenticationContext context) {
    PrincipalType principalType = PrincipalType.fromValue(context.getPrincipalType());
    if (principalType != PrincipalType.API_KEY) {
      throw new GatewayUnauthorizedException("Invalid API key principal");
    }
    String principalId = requireText(context.getPrincipalId(), "Invalid API key principal");
    Long tenantId = requirePositiveLong(context.getTenantId(), "Missing tenant context");
    String tenantCode = requireText(context.getTenantCode(), "Missing tenant context");
    List<String> roles = context.getRoles() == null ? List.of() : context.getRoles();
    return new VerifiedAccessToken(
        PrincipalType.API_KEY,
        principalId,
        properties.getIssuer().trim(),
        principalId,
        null,
        null,
        null,
        tenantId,
        tenantCode,
        trimToNull(context.getUsername()),
        trimToNull(context.getEmail()),
        roles,
        false,
        null,
        null);
  }

  private String requireText(String value, String message) {
    String normalized = trimToNull(value);
    if (normalized == null) {
      throw new GatewayUnauthorizedException(message);
    }
    return normalized;
  }

  private Long requirePositiveLong(Long value, String message) {
    if (value == null || value <= 0L) {
      throw new GatewayUnauthorizedException(message);
    }
    return value;
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }
}
