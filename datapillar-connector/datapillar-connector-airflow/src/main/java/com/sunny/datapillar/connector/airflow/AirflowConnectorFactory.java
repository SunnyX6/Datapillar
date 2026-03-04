package com.sunny.datapillar.connector.airflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.connector.airflow.config.AirflowConnectorConfig;
import com.sunny.datapillar.connector.airflow.domain.WorkflowService;
import com.sunny.datapillar.connector.airflow.error.AirflowErrorMapper;
import com.sunny.datapillar.connector.airflow.transport.http.AirflowHttpClient;
import com.sunny.datapillar.connector.spi.ConfigOption;
import com.sunny.datapillar.connector.spi.Connector;
import com.sunny.datapillar.connector.spi.ConnectorFactory;
import com.sunny.datapillar.connector.spi.ConnectorFactoryContext;
import java.time.Duration;
import java.util.Set;

/** Airflow connector factory. */
public class AirflowConnectorFactory implements ConnectorFactory {

  private static final ConfigOption<String> ENDPOINT = ConfigOption.of("endpoint", String.class);
  private static final ConfigOption<String> PLUGIN_PATH =
      ConfigOption.of("pluginPath", String.class);
  private static final ConfigOption<String> USERNAME = ConfigOption.of("username", String.class);
  private static final ConfigOption<String> PASSWORD = ConfigOption.of("password", String.class);
  private static final ConfigOption<String> CONNECT_TIMEOUT_MS =
      ConfigOption.of("connectTimeoutMs", String.class);
  private static final ConfigOption<String> READ_TIMEOUT_MS =
      ConfigOption.of("readTimeoutMs", String.class);

  @Override
  public String connectorIdentifier() {
    return AirflowConnector.CONNECTOR_ID;
  }

  @Override
  public Set<ConfigOption<?>> requiredOptions() {
    return Set.of(ENDPOINT, PLUGIN_PATH, USERNAME, PASSWORD);
  }

  @Override
  public Set<ConfigOption<?>> optionalOptions() {
    return Set.of(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
  }

  @Override
  public Connector create(ConnectorFactoryContext context) {
    AirflowConnectorConfig config =
        new AirflowConnectorConfig(
            context.requireOption(ENDPOINT.key()),
            context.requireOption(PLUGIN_PATH.key()),
            context.requireOption(USERNAME.key()),
            context.requireOption(PASSWORD.key()),
            parseDuration(context.option(CONNECT_TIMEOUT_MS.key()), Duration.ofSeconds(2)),
            parseDuration(context.option(READ_TIMEOUT_MS.key()), Duration.ofSeconds(8)));

    ObjectMapper objectMapper = new ObjectMapper();
    AirflowErrorMapper errorMapper = new AirflowErrorMapper();
    AirflowHttpClient httpClient = new AirflowHttpClient(config, objectMapper, errorMapper);
    WorkflowService workflowService = new WorkflowService(httpClient, objectMapper);
    return new AirflowConnector(workflowService);
  }

  private Duration parseDuration(String timeoutMs, Duration defaultDuration) {
    if (timeoutMs == null || timeoutMs.isBlank()) {
      return defaultDuration;
    }
    try {
      return Duration.ofMillis(Long.parseLong(timeoutMs.trim()));
    } catch (NumberFormatException ignored) {
      return defaultDuration;
    }
  }
}
