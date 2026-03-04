package com.sunny.datapillar.gateway.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway authentication configuration properties Bearer gateway authentication rules and request
 * extraction configuration
 *
 * @author Sunny
 * @date 2026-02-19
 */
@Data
@ConfigurationProperties(prefix = "security.authentication")
public class AuthenticationProperties {

  private boolean enabled = true;
  private String authTokenCookieName = "auth-token";
  private String issuerUri;
  private String jwkSetUri;
  private String audience;
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
}
