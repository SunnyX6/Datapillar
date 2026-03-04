package com.sunny.datapillar.connector.spi;

import java.util.Collections;
import java.util.Map;

/** Runtime context passed to factory while creating connector instance. */
public record ConnectorFactoryContext(String connectorId, Map<String, String> options) {

  public ConnectorFactoryContext {
    if (connectorId == null || connectorId.isBlank()) {
      throw new IllegalArgumentException("Connector id must not be blank");
    }
    options = options == null ? Collections.emptyMap() : Collections.unmodifiableMap(options);
  }

  public String requireOption(String key) {
    String value = options.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing connector option: " + key);
    }
    return value;
  }

  public String option(String key) {
    return options.get(key);
  }
}
