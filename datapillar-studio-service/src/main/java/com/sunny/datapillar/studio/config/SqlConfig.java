package com.sunny.datapillar.studio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * SQLConfiguration responsibleSQLConfigure assembly withBeaninitialization
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "sql")
public class SqlConfig {

  /** Whether to enable SQL execute */
  private boolean enabled = true;

  /** Maximum number of rows returned */
  private int maxRows = 10000;

  /** Execution timeout（milliseconds） */
  private long executionTimeout = 300000;
}
