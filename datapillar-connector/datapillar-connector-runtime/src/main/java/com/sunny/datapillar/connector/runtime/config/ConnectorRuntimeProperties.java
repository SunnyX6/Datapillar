package com.sunny.datapillar.connector.runtime.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connector runtime properties. */
@Data
@ConfigurationProperties(prefix = "connector")
public class ConnectorRuntimeProperties {

  private Duration defaultTimeout = Duration.ofSeconds(8);

  private Integer defaultMaxAttempts = 2;

  private Duration defaultBackoff = Duration.ofMillis(200);

  private Map<String, ConnectorInstanceProperties> instances = new LinkedHashMap<>();
}
