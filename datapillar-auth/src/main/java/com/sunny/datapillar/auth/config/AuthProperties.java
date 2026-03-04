package com.sunny.datapillar.auth.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Auth module runtime configuration. */
@Data
@Validated
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

  private AuthenticatorType authenticator = AuthenticatorType.SIMPLE;
  private Token token = new Token();
  private Jwks jwks = new Jwks();
  private OAuth2 oauth2 = new OAuth2();

  @PostConstruct
  public void validate() {
    if (authenticator == null) {
      throw new IllegalStateException("auth.authenticator must be configured");
    }
    token.validate();
    jwks.validate();
    oauth2.validate();
  }

  public enum AuthenticatorType {
    SIMPLE,
    OAUTH2;

    public static AuthenticatorType from(String value) {
      if (value == null || value.isBlank()) {
        throw new IllegalStateException("auth.authenticator cannot be empty");
      }
      String normalized = value.trim().toUpperCase(Locale.ROOT);
      if ("SIMPLE".equals(normalized)) {
        return SIMPLE;
      }
      if ("OAUTH2".equals(normalized)) {
        return OAUTH2;
      }
      throw new IllegalStateException("auth.authenticator must be simple or oauth2");
    }
  }

  @Data
  public static class Token {

    @NotBlank private String issuer = "https://auth.datapillar.local";

    @NotBlank private String audience = "datapillar-api";

    @NotBlank private String algorithm = "EdDSA";

    @NotBlank private String privateKeyPath = "classpath:security/auth-token-dev-private.pem";

    @NotBlank private String publicKeyPath = "classpath:security/auth-token-dev-public.pem";

    private long accessTtlSeconds = 3600;
    private long refreshTtlSeconds = 604800;
    private long refreshRememberTtlSeconds = 2592000;
    private long loginTtlSeconds = 300;

    public List<String> audiences() {
      List<String> normalized = new ArrayList<>();
      for (String tokenValue : audience.split(",")) {
        if (tokenValue == null) {
          continue;
        }
        String value = tokenValue.trim();
        if (!value.isEmpty()) {
          normalized.add(value);
        }
      }
      return normalized;
    }

    private void validate() {
      if (!"EdDSA".equalsIgnoreCase(algorithm)) {
        throw new IllegalStateException("auth.token.algorithm must be EdDSA");
      }
      if (audiences().isEmpty()) {
        throw new IllegalStateException("auth.token.audience cannot be empty");
      }
      if (accessTtlSeconds <= 0 || refreshTtlSeconds <= 0 || refreshRememberTtlSeconds <= 0) {
        throw new IllegalStateException("auth.token ttl must be > 0");
      }
      if (loginTtlSeconds <= 0) {
        throw new IllegalStateException("auth.token.login_ttl_seconds must be > 0");
      }
    }
  }

  @Data
  public static class Jwks {

    private boolean enabled = true;

    @NotBlank private String activeKid = "auth-dev-2026-01";

    private void validate() {
      if (enabled && (activeKid == null || activeKid.trim().isEmpty())) {
        throw new IllegalStateException("auth.jwks.active_kid cannot be empty");
      }
    }
  }

  @Data
  public static class OAuth2 {

    @NotBlank private String provider = "generic";

    @NotBlank private String authority = "https://idp.example.com";

    @NotBlank private String jwksUri = "https://idp.example.com/.well-known/jwks.json";

    private String staticPublicKeyPath;

    @NotEmpty
    private List<String> principalFields =
        new ArrayList<>(Arrays.asList("preferred_username", "email", "sub"));

    private void validate() {
      if (provider == null || provider.isBlank()) {
        throw new IllegalStateException("auth.oauth2.provider cannot be empty");
      }
      if (authority == null || authority.isBlank()) {
        throw new IllegalStateException("auth.oauth2.authority cannot be empty");
      }
      if (jwksUri == null || jwksUri.isBlank()) {
        throw new IllegalStateException("auth.oauth2.jwks_uri cannot be empty");
      }
      if (principalFields == null || principalFields.isEmpty()) {
        throw new IllegalStateException("auth.oauth2.principal_fields cannot be empty");
      }
    }
  }
}
