package com.sunny.datapillar.connector.gravitino.config;

import java.time.Duration;

/** Gravitino connector configuration. */
public record GravitinoConnectorConfig(
    String endpoint,
    String metadataMetalake,
    String semanticMetalake,
    String semanticCatalog,
    String semanticSchema,
    String simpleAuthUser,
    Duration connectTimeout,
    Duration readTimeout) {

  public GravitinoConnectorConfig {
    if (endpoint == null || endpoint.isBlank()) {
      throw new IllegalArgumentException("Gravitino endpoint must not be blank");
    }
    if (metadataMetalake == null || metadataMetalake.isBlank()) {
      throw new IllegalArgumentException("Gravitino metadata metalake must not be blank");
    }
    if (semanticMetalake == null || semanticMetalake.isBlank()) {
      throw new IllegalArgumentException("Gravitino semantic metalake must not be blank");
    }
    semanticCatalog =
        semanticCatalog == null || semanticCatalog.isBlank() ? "OneDS" : semanticCatalog;
    semanticSchema = semanticSchema == null || semanticSchema.isBlank() ? "OneDS" : semanticSchema;
    simpleAuthUser =
        simpleAuthUser == null || simpleAuthUser.isBlank() ? "datapillar" : simpleAuthUser;
    connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
    readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
  }

  public String normalizedEndpoint() {
    if (endpoint.endsWith("/")) {
      return endpoint.substring(0, endpoint.length() - 1);
    }
    return endpoint;
  }
}
