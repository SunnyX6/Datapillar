package com.sunny.datapillar.connector.runtime.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/** Per-connector instance properties. */
@Data
public class ConnectorInstanceProperties {

  private Map<String, String> options = new LinkedHashMap<>();

  private Duration timeout;

  private Integer maxAttempts;

  private Duration backoff;
}
