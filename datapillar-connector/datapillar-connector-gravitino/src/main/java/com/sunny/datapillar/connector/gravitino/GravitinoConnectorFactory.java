package com.sunny.datapillar.connector.gravitino;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.connector.gravitino.config.GravitinoConnectorConfig;
import com.sunny.datapillar.connector.gravitino.domain.metadata.CatalogService;
import com.sunny.datapillar.connector.gravitino.domain.metadata.MetalakeService;
import com.sunny.datapillar.connector.gravitino.domain.metadata.SchemaService;
import com.sunny.datapillar.connector.gravitino.domain.metadata.TableService;
import com.sunny.datapillar.connector.gravitino.domain.metadata.TagService;
import com.sunny.datapillar.connector.gravitino.domain.security.RolePrivilegeService;
import com.sunny.datapillar.connector.gravitino.domain.security.UserService;
import com.sunny.datapillar.connector.gravitino.domain.semantic.MetricService;
import com.sunny.datapillar.connector.gravitino.domain.semantic.ModifierService;
import com.sunny.datapillar.connector.gravitino.domain.semantic.UnitService;
import com.sunny.datapillar.connector.gravitino.domain.semantic.ValueDomainService;
import com.sunny.datapillar.connector.gravitino.domain.semantic.WordRootService;
import com.sunny.datapillar.connector.gravitino.error.GravitinoErrorMapper;
import com.sunny.datapillar.connector.gravitino.transport.sdk.GravitinoMetadataClient;
import com.sunny.datapillar.connector.gravitino.transport.sdk.GravitinoSdkClientFactory;
import com.sunny.datapillar.connector.gravitino.transport.sdk.GravitinoSecurityClient;
import com.sunny.datapillar.connector.gravitino.transport.sdk.GravitinoSemanticClient;
import com.sunny.datapillar.connector.spi.ConfigOption;
import com.sunny.datapillar.connector.spi.Connector;
import com.sunny.datapillar.connector.spi.ConnectorFactory;
import com.sunny.datapillar.connector.spi.ConnectorFactoryContext;
import java.time.Duration;
import java.util.Set;

/** Gravitino connector factory. */
public class GravitinoConnectorFactory implements ConnectorFactory {

  private static final ConfigOption<String> ENDPOINT = ConfigOption.of("endpoint", String.class);
  private static final ConfigOption<String> METADATA_METALAKE =
      ConfigOption.of("metalakes.metadata", String.class);
  private static final ConfigOption<String> SEMANTIC_METALAKE =
      ConfigOption.of("metalakes.semantic", String.class);
  private static final ConfigOption<String> SEMANTIC_CATALOG =
      ConfigOption.of("semantic.catalog", String.class);
  private static final ConfigOption<String> SEMANTIC_SCHEMA =
      ConfigOption.of("semantic.schema", String.class);
  private static final ConfigOption<String> SIMPLE_AUTH_USER =
      ConfigOption.of("simpleAuthUser", String.class);
  private static final ConfigOption<String> CONNECT_TIMEOUT_MS =
      ConfigOption.of("connectTimeoutMs", String.class);
  private static final ConfigOption<String> READ_TIMEOUT_MS =
      ConfigOption.of("readTimeoutMs", String.class);

  @Override
  public String connectorIdentifier() {
    return GravitinoConnector.CONNECTOR_ID;
  }

  @Override
  public Set<ConfigOption<?>> requiredOptions() {
    return Set.of(ENDPOINT, METADATA_METALAKE, SEMANTIC_METALAKE);
  }

  @Override
  public Set<ConfigOption<?>> optionalOptions() {
    return Set.of(
        SEMANTIC_CATALOG, SEMANTIC_SCHEMA, SIMPLE_AUTH_USER, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
  }

  @Override
  public Connector create(ConnectorFactoryContext context) {
    GravitinoConnectorConfig config =
        new GravitinoConnectorConfig(
            context.requireOption(ENDPOINT.key()),
            context.requireOption(METADATA_METALAKE.key()),
            context.requireOption(SEMANTIC_METALAKE.key()),
            context.option(SEMANTIC_CATALOG.key()),
            context.option(SEMANTIC_SCHEMA.key()),
            context.option(SIMPLE_AUTH_USER.key()),
            parseDuration(context.option(CONNECT_TIMEOUT_MS.key()), Duration.ofSeconds(2)),
            parseDuration(context.option(READ_TIMEOUT_MS.key()), Duration.ofSeconds(5)));

    ObjectMapper objectMapper = new ObjectMapper();
    GravitinoErrorMapper errorMapper = new GravitinoErrorMapper();
    GravitinoSdkClientFactory sdkClientFactory = new GravitinoSdkClientFactory(config);

    GravitinoMetadataClient metadataClient =
        new GravitinoMetadataClient(sdkClientFactory, objectMapper, errorMapper);
    GravitinoSemanticClient semanticClient =
        new GravitinoSemanticClient(sdkClientFactory, objectMapper, errorMapper);
    GravitinoSecurityClient securityClient =
        new GravitinoSecurityClient(sdkClientFactory, objectMapper, errorMapper);

    MetalakeService metalakeService = new MetalakeService(metadataClient);
    CatalogService catalogService = new CatalogService(metadataClient);
    SchemaService schemaService = new SchemaService(metadataClient);
    TableService tableService = new TableService(metadataClient);
    TagService tagService = new TagService(metadataClient);

    UserService userService = new UserService(securityClient);
    RolePrivilegeService rolePrivilegeService = new RolePrivilegeService(securityClient);

    WordRootService wordRootService = new WordRootService(semanticClient);
    MetricService metricService = new MetricService(semanticClient);
    UnitService unitService = new UnitService(semanticClient);
    ModifierService modifierService = new ModifierService(semanticClient);
    ValueDomainService valueDomainService = new ValueDomainService(semanticClient);

    return new GravitinoConnector(
        objectMapper,
        metalakeService,
        catalogService,
        schemaService,
        tableService,
        tagService,
        userService,
        rolePrivilegeService,
        wordRootService,
        metricService,
        unitService,
        modifierService,
        valueDomainService);
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
