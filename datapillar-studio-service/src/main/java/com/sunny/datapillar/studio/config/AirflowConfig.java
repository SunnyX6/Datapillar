package com.sunny.datapillar.studio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Airflow integration configuration. */
@Data
@Configuration
@ConfigurationProperties(prefix = "airflow")
public class AirflowConfig {

  /** Airflow endpoint */
  private String endpoint;

  /** Datapillar plugin path */
  private String pluginPath = "/plugins/datapillar";

  /** Airflow username */
  private String username;

  /** Airflow password */
  private String password;

  /** Connect timeout in milliseconds */
  private Integer connectTimeoutMs = 5000;

  /** Read timeout in milliseconds */
  private Integer readTimeoutMs = 30000;
}
