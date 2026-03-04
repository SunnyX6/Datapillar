package com.sunny.datapillar.auth.service;

import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.auth.key.JwksPublisher;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Token metadata application service. */
@Service
public class TokenAppService {

  private final AuthProperties authProperties;
  private final JwksPublisher jwksPublisher;

  public TokenAppService(AuthProperties authProperties, JwksPublisher jwksPublisher) {
    this.authProperties = authProperties;
    this.jwksPublisher = jwksPublisher;
  }

  public Map<String, Object> jwks() {
    return jwksPublisher.publish();
  }

  public Map<String, Object> openidConfiguration() {
    String issuer = trimTrailingSlash(authProperties.getToken().getIssuer());
    Map<String, Object> discovery = new LinkedHashMap<>();
    discovery.put("issuer", issuer);
    discovery.put("jwks_uri", issuer + "/.well-known/jwks.json");
    discovery.put("token_endpoint", issuer + "/oauth2/token");
    discovery.put("authorization_endpoint", issuer + "/auth/session/oauth2/authorize");
    discovery.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
    discovery.put("token_endpoint_auth_methods_supported", List.of("none"));
    discovery.put("response_types_supported", List.of("code"));
    return discovery;
  }

  private String trimTrailingSlash(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    String normalized = value.trim();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
