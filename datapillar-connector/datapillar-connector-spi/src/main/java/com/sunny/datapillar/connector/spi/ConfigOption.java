package com.sunny.datapillar.connector.spi;

import java.util.Objects;

/**
 * Connector option definition.
 *
 * @param <T> option value type
 */
public record ConfigOption<T>(String key, Class<T> valueType) {

  public ConfigOption {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("Option key must not be blank");
    }
    Objects.requireNonNull(valueType, "Option value type must not be null");
  }

  public static <T> ConfigOption<T> of(String key, Class<T> valueType) {
    return new ConfigOption<>(key, valueType);
  }
}
