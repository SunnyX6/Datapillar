package com.sunny.datapillar.studio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * GravitinoConfiguration responsibleGravitinoConfigure assembly withBeaninitialization
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gravitino")
public class GravitinoConfig {

  /** Gravitino Service address */
  private String uri;

  /** Metalake Name */
  private String metalake;
}
