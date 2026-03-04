package com.sunny.datapillar.connector.gravitino;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.sunny.datapillar.connector.spi.Connector;
import com.sunny.datapillar.connector.spi.ConnectorException;
import com.sunny.datapillar.connector.spi.ConnectorInvocation;
import com.sunny.datapillar.connector.spi.ConnectorManifest;
import com.sunny.datapillar.connector.spi.ConnectorResponse;
import com.sunny.datapillar.connector.spi.ErrorType;
import com.sunny.datapillar.connector.spi.OperationSpec;
import java.util.Map;

/** Gravitino connector implementation. */
public class GravitinoConnector implements Connector {

  public static final String CONNECTOR_ID = "gravitino";

  public static final String OP_METADATA_PROXY = "gravitino.metadata.proxy";
  public static final String OP_SEMANTIC_PROXY = "gravitino.semantic.proxy";
  public static final String OP_METALAKE_LIST = "gravitino.metadata.metalake.list";
  public static final String OP_METALAKE_LOAD = "gravitino.metadata.metalake.load";
  public static final String OP_METALAKE_CREATE = "gravitino.metadata.metalake.create";
  public static final String OP_SECURITY_SYNC_USER = "gravitino.security.user.sync";
  public static final String OP_SECURITY_LIST_ROLE_DATA_PRIVILEGES =
      "gravitino.security.role.dataPrivilege.list";
  public static final String OP_SECURITY_SYNC_ROLE_DATA_PRIVILEGES =
      "gravitino.security.role.dataPrivilege.sync";

  private final ObjectMapper objectMapper;
  private final MetalakeService metalakeService;
  private final CatalogService catalogService;
  private final SchemaService schemaService;
  private final TableService tableService;
  private final TagService tagService;
  private final UserService userService;
  private final RolePrivilegeService rolePrivilegeService;
  private final WordRootService wordRootService;
  private final MetricService metricService;
  private final UnitService unitService;
  private final ModifierService modifierService;
  private final ValueDomainService valueDomainService;

  public GravitinoConnector(
      ObjectMapper objectMapper,
      MetalakeService metalakeService,
      CatalogService catalogService,
      SchemaService schemaService,
      TableService tableService,
      TagService tagService,
      UserService userService,
      RolePrivilegeService rolePrivilegeService,
      WordRootService wordRootService,
      MetricService metricService,
      UnitService unitService,
      ModifierService modifierService,
      ValueDomainService valueDomainService) {
    this.objectMapper = objectMapper;
    this.metalakeService = metalakeService;
    this.catalogService = catalogService;
    this.schemaService = schemaService;
    this.tableService = tableService;
    this.tagService = tagService;
    this.userService = userService;
    this.rolePrivilegeService = rolePrivilegeService;
    this.wordRootService = wordRootService;
    this.metricService = metricService;
    this.unitService = unitService;
    this.modifierService = modifierService;
    this.valueDomainService = valueDomainService;
  }

  @Override
  public ConnectorManifest manifest() {
    return new ConnectorManifest(
        CONNECTOR_ID,
        "1.0.0",
        Map.ofEntries(
            Map.entry(OP_METADATA_PROXY, OperationSpec.write(OP_METADATA_PROXY)),
            Map.entry(OP_SEMANTIC_PROXY, OperationSpec.write(OP_SEMANTIC_PROXY)),
            Map.entry(OP_METALAKE_LIST, OperationSpec.read(OP_METALAKE_LIST)),
            Map.entry(OP_METALAKE_LOAD, OperationSpec.read(OP_METALAKE_LOAD)),
            Map.entry(OP_METALAKE_CREATE, OperationSpec.write(OP_METALAKE_CREATE)),
            Map.entry(OP_SECURITY_SYNC_USER, OperationSpec.write(OP_SECURITY_SYNC_USER)),
            Map.entry(
                OP_SECURITY_LIST_ROLE_DATA_PRIVILEGES,
                OperationSpec.read(OP_SECURITY_LIST_ROLE_DATA_PRIVILEGES)),
            Map.entry(
                OP_SECURITY_SYNC_ROLE_DATA_PRIVILEGES,
                OperationSpec.write(OP_SECURITY_SYNC_ROLE_DATA_PRIVILEGES))));
  }

  @Override
  public ConnectorResponse invoke(ConnectorInvocation invocation) {
    String operationId = invocation.operationId();
    JsonNode payload = invocation.payload();

    JsonNode response =
        switch (operationId) {
          case OP_METADATA_PROXY -> handleMetadataProxy(payload, invocation);
          case OP_SEMANTIC_PROXY -> handleSemanticProxy(payload, invocation);
          case OP_METALAKE_LIST -> metalakeService.listMetalakes(invocation.context());
          case OP_METALAKE_LOAD ->
              metalakeService.loadMetalake(
                  requiredText(payload, "metalakeName"), invocation.context());
          case OP_METALAKE_CREATE ->
              metalakeService.createMetalake(
                  requiredText(payload, "metalakeName"),
                  optionalText(payload, "comment"),
                  payload.path("properties"),
                  invocation.context());
          case OP_SECURITY_SYNC_USER ->
              userService.syncUser(requiredText(payload, "username"), invocation.context());
          case OP_SECURITY_LIST_ROLE_DATA_PRIVILEGES ->
              rolePrivilegeService.listRoleDataPrivileges(
                  requiredText(payload, "roleName"),
                  optionalText(payload, "domain"),
                  invocation.context());
          case OP_SECURITY_SYNC_ROLE_DATA_PRIVILEGES ->
              rolePrivilegeService.syncRoleDataPrivileges(
                  requiredText(payload, "roleName"),
                  optionalText(payload, "domain"),
                  payload.path("commands"),
                  invocation.context());
          default ->
              throw new ConnectorException(
                  ErrorType.BAD_REQUEST, "Unsupported gravitino operation: " + operationId);
        };

    return ConnectorResponse.of(response);
  }

  private JsonNode handleMetadataProxy(JsonNode payload, ConnectorInvocation invocation) {
    String path = requiredText(payload, "path");
    String method = optionalText(payload, "method");
    Map<String, String> query = toQueryMap(payload.path("query"));
    JsonNode body = payload.path("body");

    if (path.startsWith("/catalogs") || path.startsWith("catalogs")) {
      return catalogService.request(method, path, query, body, invocation.context());
    }
    if (path.startsWith("/objects")
        || path.startsWith("objects")
        || path.startsWith("/tags")
        || path.startsWith("tags")) {
      return tagService.request(method, path, query, body, invocation.context());
    }
    if (path.contains("/schemas/") || path.endsWith("/schemas") || path.endsWith("schemas")) {
      return schemaService.request(method, path, query, body, invocation.context());
    }
    if (path.contains("/tables/") || path.endsWith("/tables") || path.endsWith("tables")) {
      return tableService.request(method, path, query, body, invocation.context());
    }
    return catalogService.request(method, path, query, body, invocation.context());
  }

  private JsonNode handleSemanticProxy(JsonNode payload, ConnectorInvocation invocation) {
    String path = requiredText(payload, "path");
    String method = optionalText(payload, "method");
    Map<String, String> query = toQueryMap(payload.path("query"));
    JsonNode body = payload.path("body");

    if (path.startsWith("/wordroots") || path.startsWith("wordroots")) {
      return wordRootService.request(method, path, query, body, invocation.context());
    }
    if (path.startsWith("/metrics") || path.startsWith("metrics")) {
      if (path.contains("modifiers")) {
        return modifierService.request(method, path, query, body, invocation.context());
      }
      return metricService.request(method, path, query, body, invocation.context());
    }
    if (path.startsWith("/units") || path.startsWith("units")) {
      return unitService.request(method, path, query, body, invocation.context());
    }
    if (path.startsWith("/valuedomains") || path.startsWith("valuedomains")) {
      return valueDomainService.request(method, path, query, body, invocation.context());
    }
    return metricService.request(method, path, query, body, invocation.context());
  }

  private Map<String, String> toQueryMap(JsonNode queryNode) {
    if (queryNode == null || queryNode.isNull()) {
      return Map.of();
    }
    return objectMapper.convertValue(
        queryNode,
        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
  }

  private String requiredText(JsonNode payload, String field) {
    String value = payload == null ? null : payload.path(field).asText(null);
    if (value == null || value.isBlank()) {
      throw new ConnectorException(ErrorType.BAD_REQUEST, "Missing required field: " + field);
    }
    return value.trim();
  }

  private String optionalText(JsonNode payload, String field) {
    if (payload == null) {
      return null;
    }
    String value = payload.path(field).asText(null);
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
