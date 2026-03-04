package com.sunny.datapillar.auth.authentication.validator;

import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoToken;
import com.sunny.datapillar.common.security.EdDsaJwtSupport;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import org.springframework.stereotype.Component;

/** Static public-key based identity token validator. */
@Component
public class StaticKeyTokenValidator implements OAuthTokenValidator {

  private final AuthProperties authProperties;

  public StaticKeyTokenValidator(AuthProperties authProperties) {
    this.authProperties = authProperties;
  }

  @Override
  public String name() {
    return "static-key";
  }

  @Override
  public boolean supports(SsoProviderConfig config) {
    return resolvePublicKey(config) != null;
  }

  @Override
  public void validate(SsoProviderConfig config, SsoToken token) {
    String identityToken = extractIdentityToken(token);
    if (identityToken == null) {
      return;
    }
    PublicKey publicKey = resolvePublicKey(config);
    if (publicKey == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          "OAuth2 identity token static key not configured");
    }
    try {
      Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(identityToken);
    } catch (Throwable ex) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          ex, "OAuth2 identity token validation failed");
    }
  }

  private PublicKey resolvePublicKey(SsoProviderConfig config) {
    String inlinePem = config == null ? null : config.getOptionalString("idTokenPublicKeyPem");
    if (inlinePem != null && !inlinePem.isBlank()) {
      return EdDsaJwtSupport.parsePublicKey(inlinePem);
    }

    String path = config == null ? null : config.getOptionalString("idTokenPublicKeyPath");
    if (path == null || path.isBlank()) {
      path = authProperties.getOauth2().getStaticPublicKeyPath();
    }
    if (path == null || path.isBlank()) {
      return null;
    }
    try {
      String pem = Files.readString(Path.of(path), StandardCharsets.US_ASCII);
      return EdDsaJwtSupport.parsePublicKey(pem);
    } catch (Throwable ex) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException(
          ex, "OAuth2 identity token static key is invalid");
    }
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
