package com.sunny.datapillar.studio.security.keystore;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.studio.security.keystore.impl.LocalKeyStorage;
import com.sunny.datapillar.studio.security.keystore.impl.ObjectStorageKeyStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Key storage wiring. */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(KeyStorageProperties.class)
public class KeyStorageConfig {

  private final KeyStorageProperties properties;

  @Bean
  public KeyStorage keyStorage() {
    String type = normalizeType(properties.getType());
    return switch (type) {
      case "local" -> new LocalKeyStorage(properties);
      case "object" -> new ObjectStorageKeyStorage(properties);
      default -> throw new BadRequestException("Unsupported key storage type: %s", type);
    };
  }

  private String normalizeType(String type) {
    if (type == null) {
      return "local";
    }
    String normalized = type.trim().toLowerCase();
    return normalized.isEmpty() ? "local" : normalized;
  }
}
