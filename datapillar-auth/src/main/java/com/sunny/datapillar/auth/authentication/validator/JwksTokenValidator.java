package com.sunny.datapillar.auth.authentication.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoToken;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** JWKS based identity token validator. */
@Component
public class JwksTokenValidator implements OAuthTokenValidator {

  private final ObjectMapper objectMapper;
  private final AuthProperties authProperties;
  private final HttpClient httpClient;

  public JwksTokenValidator(ObjectMapper objectMapper, AuthProperties authProperties) {
    this.objectMapper = objectMapper;
    this.authProperties = authProperties;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }

  @Override
  public String name() {
    return "jwks";
  }

  @Override
  public boolean supports(SsoProviderConfig config) {
    String jwksUri = resolveJwksUri(config);
    return jwksUri != null && !jwksUri.isBlank();
  }

  @Override
  public void validate(SsoProviderConfig config, SsoToken token) {
    String identityToken = extractIdentityToken(token);
    if (identityToken == null) {
      return;
    }
    String[] segments = identityToken.split("\\.");
    if (segments.length != 3) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "OAuth2 identity token invalid");
    }
    try {
      String jwksUri = resolveJwksUri(config);
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create(jwksUri)).GET().build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new com.sunny.datapillar.common.exception.UnauthorizedException(
            "OAuth2 identity token validation failed");
      }
      JsonNode json = objectMapper.readTree(response.body());
      if (json == null
          || !json.has("keys")
          || !json.get("keys").isArray()
          || json.get("keys").isEmpty()) {
        throw new com.sunny.datapillar.common.exception.UnauthorizedException(
            "OAuth2 identity token validation failed");
      }

      // Minimal structural validation to reject malformed non-JWT token payload.
      Base64.getUrlDecoder().decode(segments[0]);
      Base64.getUrlDecoder().decode(segments[1]);
    } catch (com.sunny.datapillar.common.exception.DatapillarRuntimeException ex) {
      throw ex;
    } catch (Throwable ex) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          ex, "OAuth2 identity token validation failed");
    }
  }

  private String resolveJwksUri(SsoProviderConfig config) {
    String configValue = config == null ? null : config.getOptionalString("jwksUri");
    if (configValue != null && !configValue.isBlank()) {
      return configValue;
    }
    return authProperties.getOauth2().getJwksUri();
  }

  private String extractIdentityToken(SsoToken token) {
    if (token == null || token.getRaw() == null) {
      return null;
    }
    Object idToken = token.getRaw().get("id_token");
    if (idToken == null) {
      idToken = token.getRaw().get("idToken");
    }
    if (idToken == null) {
      return null;
    }
    String text = String.valueOf(idToken).trim();
    return text.isEmpty() ? null : text;
  }
}
