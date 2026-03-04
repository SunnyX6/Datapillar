package com.sunny.datapillar.connector.airflow.config;

import java.time.Duration;

/** Airflow connector runtime configuration. */
public record AirflowConnectorConfig(
    String endpoint,
    String pluginPath,
    String username,
    String password,
    Duration connectTimeout,
    Duration readTimeout) {

  public AirflowConnectorConfig {
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("Airflow endpoint must not be blank");
    }
    if (pluginPath == null || pluginPath.isBlank()) {
      throw new IllegalArgumentException("Airflow pluginPath must not be blank");
    }
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("Airflow username must not be blank");
    }
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("Airflow password must not be blank");
    }
    connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
    readTimeout = readTimeout == null ? Duration.ofSeconds(8) : readTimeout;
  }

  public String normalizedEndpoint() {
    if (endpoint.endsWith("/")) {
      return endpoint.substring(0, endpoint.length() - 1);
    }
    return endpoint;
  }

  public String normalizedPluginPath() {
    if (pluginPath.startsWith("/")) {
      return pluginPath;
    }
    return "/" + pluginPath;
  }

  public String pluginBaseUrl() {
    return normalizedEndpoint() + normalizedPluginPath();
  }

  public String tokenUrl() {
    return normalizedEndpoint() + "/auth/token";
  }
}
