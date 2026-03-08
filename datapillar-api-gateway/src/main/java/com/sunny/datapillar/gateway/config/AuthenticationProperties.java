package com.sunny.datapillar.gateway.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Gateway authentication configuration properties for strict access-token verification.
 *
 * @author Sunny
 * @date 2026-03-07
 */
@Data
@Validated
@ConfigurationProperties(prefix = "security.authentication")
public class AuthenticationProperties {

  private boolean enabled = true;
  private String authTokenCookieName = "auth-token";
  private String issuer;
  private String audience = "datapillar-api";
  private long jwksCacheSeconds = 300;
  private String usernameClaim = "preferred_username";
  private String emailClaim = "email";
  private List<String> protectedPathPrefixes =
      new ArrayList<>(List.of("/api/studio", "/api/ai", "/api/openlineage"));
  private List<String> publicPathPrefixes =
      new ArrayList<>(
          List.of(
              "/api/auth",
              "/api/studio/setup",
              "/api/studio/actuator/health",
              "/api/studio/v3/api-docs",
              "/api/openlineage/actuator/health",
              "/api/openlineage/v3/api-docs",
              "/api/docs"));

  @PostConstruct
  public void validate() {
    if (!StringUtils.hasText(issuer)) {
      throw new IllegalStateException("security.authentication.issuer must be configured");
    }
    if (!StringUtils.hasText(audience)) {
      throw new IllegalStateException("security.authentication.audience must be configured");
    }
    if (jwksCacheSeconds <= 0L) {
      throw new IllegalStateException("security.authentication.jwks-cache-seconds must be > 0");
    }
  }

  public String issuerJwksUri() {
    return normalizeIssuer() + "/.well-known/jwks.json";
  }

  public String issuerSessionContextUri() {
    return normalizeIssuer() + "/auth/session/context";
  }

  private String normalizeIssuer() {
    String normalized = issuer.trim();
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }
}
