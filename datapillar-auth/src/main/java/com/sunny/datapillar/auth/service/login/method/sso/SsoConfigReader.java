package com.sunny.datapillar.auth.service.login.method.sso;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.auth.config.AuthProperties;
import com.sunny.datapillar.auth.entity.TenantSsoConfig;
import com.sunny.datapillar.auth.mapper.TenantSsoConfigMapper;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoProviderConfig;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reader for SSO provider configuration.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class SsoConfigReader {

  private final TenantSsoConfigMapper tenantSsoConfigMapper;
  private final ObjectMapper objectMapper;
  private final AuthProperties authProperties;

  public SsoConfigReader(
      TenantSsoConfigMapper tenantSsoConfigMapper,
      ObjectMapper objectMapper,
      AuthProperties authProperties) {
    this.tenantSsoConfigMapper = tenantSsoConfigMapper;
    this.objectMapper = objectMapper;
    this.authProperties = authProperties;
  }

  public SsoProviderConfig loadConfig(Long tenantId, String provider) {
    TenantSsoConfig config = tenantSsoConfigMapper.selectByTenantIdAndProvider(tenantId, provider);
    if (config == null) {
      throw new com.sunny.datapillar.common.exception.NotFoundException(
          "SSO configuration not found: provider=%s", provider);
    }
    if (config.getStatus() == null || config.getStatus() != 1) {
      throw new com.sunny.datapillar.common.exception.ForbiddenException(
          "SSO configuration is disabled: provider=%s", provider);
    }
    Map<String, Object> values;
    try {
      values = objectMapper.readValue(config.getConfigJson(), new TypeReference<>() {});
    } catch (Throwable e) {
      throw new com.sunny.datapillar.common.exception.InternalException(
          "Invalid SSO configuration: %s", provider);
    }

    String clientSecret = stringValue(values.get("clientSecret"));
    if (clientSecret == null) {
      throw new com.sunny.datapillar.common.exception.InternalException(
          "Invalid SSO configuration: %s", "clientSecret");
    }

    values.putIfAbsent("provider", authProperties.getOauth2().getProvider());
    values.putIfAbsent("authority", authProperties.getOauth2().getAuthority());
    values.putIfAbsent("jwksUri", authProperties.getOauth2().getJwksUri());
    values.putIfAbsent(
        "principalFields",
        String.join(
            ",", normalizePrincipalFields(authProperties.getOauth2().getPrincipalFields())));

    return new SsoProviderConfig(config.getProvider(), config.getBaseUrl(), values);
  }

  private List<String> normalizePrincipalFields(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of("preferred_username", "email", "sub");
    }
    return values;
  }

  private String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isBlank() ? null : text;
  }
}
