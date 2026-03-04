package com.sunny.datapillar.auth.service.login.method.sso.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * SSO provider configuration model.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@AllArgsConstructor
public class SsoProviderConfig {
  private String provider;
  private String baseUrl;
  private Map<String, Object> config;

  public String getRequiredString(String key) {
    Object value = config != null ? config.get(key) : null;
    if (value == null || String.valueOf(value).isBlank()) {
      throw new com.sunny.datapillar.common.exception.InternalException(
          "Invalid SSO configuration: %s", key);
    }
    return String.valueOf(value).trim();
  }

  public String getOptionalString(String key) {
    Object value = config != null ? config.get(key) : null;
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isBlank() ? null : text;
  }
}
