package com.sunny.datapillar.studio.integration.gravitino;

import com.sunny.datapillar.common.constant.HeaderConstants;
import com.sunny.datapillar.studio.config.GravitinoConfig;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.util.UserContextUtil;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.gravitino.client.DefaultOAuth2TokenProvider;
import org.apache.gravitino.client.GravitinoAdminClient;
import org.apache.gravitino.client.GravitinoClient;
import org.apache.gravitino.client.GravitinoClientBase;
import org.apache.gravitino.client.GravitinoClientConfiguration;
import org.apache.gravitino.client.KerberosTokenProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Factory for Gravitino Java clients used by studio-service. */
@Component
public class GravitinoClientFactory {

  private static final String AUTH_TYPE_SIMPLE = "simple";
  private static final String AUTH_TYPE_OAUTH = "oauth";
  private static final String AUTH_TYPE_KERBEROS = "kerberos";
  private static final long SERVICE_EXTERNAL_USER_ID = -1L;

  private final GravitinoConfig config;

  public GravitinoClientFactory(GravitinoConfig config) {
    this.config = config;
  }

  public GravitinoAdminClient createAdminClient() {
    return createAdminClient(null);
  }

  public GravitinoAdminClient createAdminClient(String principalUsername) {
    return createAdminClient(principalUsername, null);
  }

  public GravitinoAdminClient createAdminClient(String principalUsername, Long externalUserId) {
    GravitinoAdminClient.AdminClientBuilder builder =
        GravitinoAdminClient.builder(requiredText(config.getUri(), "gravitino.uri"));
    applyOptions(builder, principalUsername, externalUserId);
    return builder.build();
  }

  public GravitinoClient createMetadataClient() {
    return createMetadataClient(null);
  }

  public GravitinoClient createMetadataClient(String principalUsername) {
    return createClient(requiredMetalake(), principalUsername);
  }

  public GravitinoClient createSemanticClient() {
    return createSemanticClient(null);
  }

  public GravitinoClient createSemanticClient(String principalUsername) {
    return createClient(requiredMetalake(), principalUsername);
  }

  public GravitinoClient createClient(String metalake, String principalUsername) {
    GravitinoClient.ClientBuilder builder =
        GravitinoClient.builder(requiredText(config.getUri(), "gravitino.uri"))
            .withMetalake(requireManagedMetalake(metalake));
    applyOptions(builder, principalUsername);
    return builder.build();
  }

  public String requireManagedMetalake(String metalake) {
    String normalizedMetalake = requiredText(metalake, "metalake");
    if (normalizedMetalake.equals(requiredMetalake())) {
      return normalizedMetalake;
    }
    throw new IllegalArgumentException("Unsupported metalake: " + normalizedMetalake);
  }

  public String requiredMetalake() {
    return requiredText(config.getMetalake(), "gravitino.metalake");
  }

  public String requiredSemanticCatalog() {
    return requiredText(config.getSemanticCatalog(), "gravitino.semantic-catalog");
  }

  public String requiredSemanticSchema() {
    return requiredText(config.getSemanticSchema(), "gravitino.semantic-schema");
  }

  public String resolveSetupPrincipalUsername() {
    String authType = normalizeAuthType(resolveAuthType());
    if (!AUTH_TYPE_SIMPLE.equals(authType)) {
      return null;
    }
    return requiredText(
        resolveConfiguredSimpleAuthUser(),
        "gravitino.auth.simple.user or gravitino.simple-auth-user");
  }

  private void applyOptions(
      GravitinoClientBase.Builder<?> builder, String principalUsername, Long externalUserId) {
    applyAuth(builder, principalUsername);
    applyContextHeaders(builder, resolveExternalUserId(principalUsername, externalUserId));
    builder.withVersionCheckDisabled().withClientConfig(clientConfig());
  }

  private void applyOptions(GravitinoClientBase.Builder<?> builder, String principalUsername) {
    applyOptions(builder, principalUsername, null);
  }

  private void applyAuth(GravitinoClientBase.Builder<?> builder, String principalUsername) {
    String authType = normalizeAuthType(resolveAuthType());
    switch (authType) {
      case AUTH_TYPE_SIMPLE -> builder.withSimpleAuth(resolveSimpleAuthUser(principalUsername));
      case AUTH_TYPE_OAUTH ->
          builder.withOAuth(
              DefaultOAuth2TokenProvider.builder()
                  .withUri(
                      requiredText(oauthConfig().getServerUri(), "gravitino.auth.oauth.server-uri"))
                  .withPath(
                      requiredText(oauthConfig().getTokenPath(), "gravitino.auth.oauth.token-path"))
                  .withCredential(
                      requiredText(
                          oauthConfig().getCredential(), "gravitino.auth.oauth.credential"))
                  .withScope(requiredText(oauthConfig().getScope(), "gravitino.auth.oauth.scope"))
                  .build());
      case AUTH_TYPE_KERBEROS -> {
        KerberosTokenProvider.Builder kerberosBuilder =
            KerberosTokenProvider.builder()
                .withClientPrincipal(
                    requiredText(
                        kerberosConfig().getClientPrincipal(),
                        "gravitino.auth.kerberos.client-principal"));
        if (StringUtils.hasText(kerberosConfig().getKeytabPath())) {
          kerberosBuilder.withKeyTabFile(new File(kerberosConfig().getKeytabPath().trim()));
        }
        builder.withKerberosAuth(kerberosBuilder.build());
      }
      default -> throw new IllegalStateException("Unsupported Gravitino auth type: " + authType);
    }
  }

  private void applyContextHeaders(GravitinoClientBase.Builder<?> builder, Long externalUserId) {
    Long tenantId = TenantContextHolder.getTenantId();
    String tenantCode = TenantContextHolder.getTenantCode();
    Map<String, String> headers = new HashMap<>();
    if (tenantId != null && tenantId > 0 && StringUtils.hasText(tenantCode)) {
      headers.put(HeaderConstants.HEADER_TENANT_ID, String.valueOf(tenantId));
      headers.put(HeaderConstants.HEADER_TENANT_CODE, tenantCode.trim());
    }
    if (externalUserId != null) {
      headers.put(HeaderConstants.HEADER_EXTERNAL_USER_ID, String.valueOf(externalUserId));
    }
    if (!headers.isEmpty()) {
      builder.withHeaders(headers);
    }
  }

  private Long resolveExternalUserId(String principalUsername, Long externalUserId) {
    if (externalUserId != null) {
      return externalUserId;
    }
    String configuredSimpleAuthUser = resolveConfiguredSimpleAuthUser();
    if (!StringUtils.hasText(principalUsername) || !StringUtils.hasText(configuredSimpleAuthUser)) {
      return null;
    }
    return principalUsername.trim().equals(configuredSimpleAuthUser.trim())
        ? SERVICE_EXTERNAL_USER_ID
        : null;
  }

  private Map<String, String> clientConfig() {
    Map<String, String> properties = new LinkedHashMap<>();
    properties.put(
        GravitinoClientConfiguration.CLIENT_CONNECTION_TIMEOUT_MS,
        String.valueOf(resolvePositive(config.getConnectTimeoutMs(), 2000)));
    properties.put(
        GravitinoClientConfiguration.CLIENT_SOCKET_TIMEOUT_MS,
        String.valueOf(resolvePositive(config.getReadTimeoutMs(), 5000)));
    return properties;
  }

  private String resolveSimpleAuthUser(String principalUsername) {
    if (StringUtils.hasText(principalUsername)) {
      return principalUsername.trim();
    }
    String requestUsername = UserContextUtil.getUsername();
    if (StringUtils.hasText(requestUsername)) {
      return requestUsername.trim();
    }
    throw new IllegalStateException("Missing authenticated username for Gravitino simple auth");
  }

  private String resolveConfiguredSimpleAuthUser() {
    GravitinoConfig.AuthConfig authConfig = config.getAuth();
    if (authConfig != null && authConfig.getSimple() != null) {
      String authUser = authConfig.getSimple().getUser();
      if (StringUtils.hasText(authUser)) {
        return authUser.trim();
      }
    }
    if (StringUtils.hasText(config.getSimpleAuthUser())) {
      return config.getSimpleAuthUser().trim();
    }
    return null;
  }

  private String resolveAuthType() {
    GravitinoConfig.AuthConfig authConfig = config.getAuth();
    if (authConfig == null || !StringUtils.hasText(authConfig.getType())) {
      return AUTH_TYPE_SIMPLE;
    }
    return authConfig.getType().trim();
  }

  private GravitinoConfig.OAuthAuthConfig oauthConfig() {
    GravitinoConfig.AuthConfig authConfig = config.getAuth();
    if (authConfig == null || authConfig.getOauth() == null) {
      throw new IllegalStateException("Missing required config: gravitino.auth.oauth");
    }
    return authConfig.getOauth();
  }

  private GravitinoConfig.KerberosAuthConfig kerberosConfig() {
    GravitinoConfig.AuthConfig authConfig = config.getAuth();
    if (authConfig == null || authConfig.getKerberos() == null) {
      throw new IllegalStateException("Missing required config: gravitino.auth.kerberos");
    }
    return authConfig.getKerberos();
  }

  private String normalizeAuthType(String authType) {
    if (!StringUtils.hasText(authType)) {
      return AUTH_TYPE_SIMPLE;
    }
    String normalized = authType.trim().toLowerCase(Locale.ROOT);
    if (AUTH_TYPE_SIMPLE.equals(normalized)
        || AUTH_TYPE_OAUTH.equals(normalized)
        || AUTH_TYPE_KERBEROS.equals(normalized)) {
      return normalized;
    }
    throw new IllegalStateException("Unsupported Gravitino auth type: " + authType);
  }

  private String requiredText(String value, String propertyName) {
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException("Missing required config: " + propertyName);
    }
    return value.trim();
  }

  private int resolvePositive(Integer value, int defaultValue) {
    if (value == null || value <= 0) {
      return defaultValue;
    }
    return value;
  }
}
