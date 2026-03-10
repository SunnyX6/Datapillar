package com.sunny.datapillar.openlineage.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.openlineage.model.Catalog;
import com.sunny.datapillar.openlineage.model.Metric;
import com.sunny.datapillar.openlineage.model.Modifier;
import com.sunny.datapillar.openlineage.model.Schema;
import com.sunny.datapillar.openlineage.model.Table;
import com.sunny.datapillar.openlineage.model.Tag;
import com.sunny.datapillar.openlineage.model.TagRelation;
import com.sunny.datapillar.openlineage.model.Unit;
import com.sunny.datapillar.openlineage.model.ValueDomain;
import com.sunny.datapillar.openlineage.model.WordRoot;
import com.sunny.datapillar.openlineage.source.event.OpenLineageEvent;
import io.openlineage.client.OpenLineage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.util.StringUtils;

/** Base parser for current-model extraction from OpenLineage MQ payload. */
public abstract class AbstractOpenLineageSource implements OpenLineageSource {

  private final ObjectMapper openLineageObjectMapper;

  protected AbstractOpenLineageSource(ObjectMapper openLineageObjectMapper) {
    this.openLineageObjectMapper = openLineageObjectMapper;
  }

  @Override
  public OpenLineageSourceModels readModels(Long tenantId, JsonNode payload) {
    OpenLineageEvent event = parseEnvelope(payload);
    OpenLineageSourceModels models = new OpenLineageSourceModels();
    models.setFacetTenantId(event.facetTenantId().orElse(null));
    models.setFacetTenantCode(event.facetTenantCode().orElse(null));
    models.setFacetTenantName(event.facetTenantName().orElse(null));

    long normalizedTenantId = tenantId == null || tenantId <= 0 ? 0L : tenantId;
    String family = resolveFamily(event);
    switch (family) {
      case "catalog" -> handleCatalogEvent(models, normalizedTenantId, event);
      case "schema" -> handleSchemaEvent(models, normalizedTenantId, event);
      case "table" -> handleTableEvent(models, normalizedTenantId, event);
      case "metric" -> handleMetricEvent(models, normalizedTenantId, event);
      case "tag" -> handleTagEvent(models, normalizedTenantId, event);
      case "wordroot" -> handleWordRootEvent(models, normalizedTenantId, event);
      case "modifier" -> handleModifierEvent(models, normalizedTenantId, event);
      case "unit" -> handleUnitEvent(models, normalizedTenantId, event);
      case "valuedomain" -> handleValueDomainEvent(models, normalizedTenantId, event);
      default -> throw new BadRequestException("Unsupported OpenLineage family: %s", family);
    }
    return models;
  }

  protected boolean matchesEngine(JsonNode payload, String engine) {
    return contains(payload.path("producer").asText(null), engine)
        || contains(payload.path("job").path("namespace").asText(null), engine)
        || contains(payload.path("job").path("name").asText(null), engine)
        || hasFacet(payload, engine);
  }

  protected boolean matchesGravitino(JsonNode payload) {
    return contains(payload.path("producer").asText(null), "gravitino")
        || startsWith(payload.path("job").path("name").asText(null), "gravitino.")
        || hasFacet(payload, "gravitino");
  }

  private OpenLineageEvent parseEnvelope(JsonNode payload) {
    try {
      if (payload.path("run").isObject()) {
        OpenLineage.RunEvent event =
            openLineageObjectMapper.treeToValue(payload, OpenLineage.RunEvent.class);
        return OpenLineageEvent.fromRunEvent(event, payload);
      }
      if (payload.path("dataset").isObject()) {
        OpenLineage.DatasetEvent event =
            openLineageObjectMapper.treeToValue(payload, OpenLineage.DatasetEvent.class);
        return OpenLineageEvent.fromDatasetEvent(event, payload);
      }
      if (payload.path("job").isObject()) {
        OpenLineage.JobEvent event =
            openLineageObjectMapper.treeToValue(payload, OpenLineage.JobEvent.class);
        return OpenLineageEvent.fromJobEvent(event, payload);
      }
      throw new BadRequestException("Unrecognized OpenLineage event type");
    } catch (BadRequestException ex) {
      throw ex;
    } catch (JsonProcessingException | IllegalArgumentException ex) {
      throw new BadRequestException(ex, "OpenLineage event parse failed");
    }
  }

  private void handleCatalogEvent(
      OpenLineageSourceModels models, long tenantId, OpenLineageEvent event) {
    String catalogName = extractResourceName(event, tenantId, "catalog", "catalog_name");
    Catalog row = new Catalog();
    row.setCatalogId(syntheticId(tenantId + "|catalog|" + catalogName));
    row.setCatalogName(catalogName);
    row.setCatalogComment("catalog event");
    models.getCatalogs().add(row);
  }

  private void handleSchemaEvent(
      OpenLineageSourceModels models, long tenantId, OpenLineageEvent event) {
    List<DatasetRef> refs = resolveDatasetRefs(event, tenantId);
    if (refs.isEmpty()) {
      Schema row = new Schema();
      row.setSchemaId(
          syntheticId(
              tenantId
                  + "|schema|"
                  + extractResourceName(event, tenantId, "schema", "schema_name")));
      row.setSchemaName(extractResourceName(event, tenantId, "schema", "schema_name"));
      row.setSchemaComment("schema event");
      models.getSchemas().add(row);
      return;
    }
    for (DatasetRef ref : refs) {
      addCatalog(models, ref.catalogId(), ref.catalogName(), null);
      addSchema(models, ref.schemaId(), ref.catalogId(), ref.schemaName(), null);
    }
  }

  private void handleTableEvent(
      OpenLineageSourceModels models, long tenantId, OpenLineageEvent event) {
    List<DatasetRef> refs = resolveDatasetRefs(event, tenantId);
    if (refs.isEmpty()) {
      Table row = new Table();
      String tableName = extractResourceName(event, tenantId, "table", "table_name");
      row.setTableId(syntheticId(tenantId + "|table|" + tableName));
      row.setTableName(tableName);
      row.setTableComment("table event");
      models.getTables().add(row);
      return;
    }
    for (DatasetRef ref : refs) {
      addCatalog(models, ref.catalogId(), ref.catalogName(), null);
      addSchema(models, ref.schemaId(), ref.catalogId(), ref.schemaName(), null);
      addTable(
          models,
          ref.tableId(),
          ref.catalogId(),
          ref.schemaId(),
          ref.schemaName(),
          ref.tableName(),
          null);
    }
  }

  private void handleMetricEvent(
      OpenLineageSourceModels models, long tenantId, OpenLineageEvent event) {
    String metricName = extractResourceName(event, tenantId, "metric", "metric_name");
    String metricCode = extractFacetValue(event.getRawEvent(), "metricCode", "metric_code", "code");
    Metric row = new Metric();
    row.setMetricId(syntheticId(tenantId + "|metric|" + firstNonBlank(metricCode, metricName)));
    row.setMetricName(metricName);
    row.setMetricCode(metricCode);
    row.setMetricType(extractFacetValue(event.getRawEvent(), "metricType", "metric_type", "type"));
    models.getMetrics().add(row);
  }

  private void handleTagEvent(
      OpenLineageSourceModels models, long tenantId, OpenLineageEvent event) {
    String tagName = extractResourceName(event, tenantId, "tag", "tag_name");
    Long tagId = syntheticId(tenantId + "|tag|" + tagName);
    Tag tag = new Tag();
    tag.setTagId(tagId);
    tag.setTagName(tagName);
    tag.setComment("tag event");
    models.getTags().add(tag);

    for (DatasetRef ref : resolveDatasetRefs(event, tenantId)) {
      addCatalog(models, ref.catalogId(), ref.catalogName(), null);
      addSchema(models, ref.schemaId(), ref.catalogId(), ref.schemaName(), null);
      addTable(
          models,
          ref.tableId(),
          ref.catalogId(),
          ref.schemaId(),
          ref.schemaName(),
          ref.tableName(),
          null);
      TagRelation relation = new TagRelation();
      relation.setTagId(tagId);
      relation.setMetadataObjectId(ref.tableId());
      relation.setMetadataObjectType("TABLE");
      models.getTagRelations().add(relation);
    }
  }

  private void handleWordRootEvent(
      OpenLineageSourceModels models, long tenantId, OpenLineageEvent event) {
    WordRoot row = new WordRoot();
    String name = extractResourceName(event, tenantId, "wordroot", "wordroot_name");
    String code = extractFacetValue(event.getRawEvent(), "wordrootCode", "wordroot_code", "code");
    row.setRootId(syntheticId(tenantId + "|wordroot|" + firstNonBlank(code, name)));
    row.setRootName(name);
    row.setRootCode(code);
    row.setRootComment("wordroot event");
    models.getWordRoots().add(row);
  }

  private void handleModifierEvent(
      OpenLineageSourceModels models, long tenantId, OpenLineageEvent event) {
    Modifier row = new Modifier();
    String name = extractResourceName(event, tenantId, "modifier", "modifier_name");
    String code = extractFacetValue(event.getRawEvent(), "modifierCode", "modifier_code", "code");
    row.setModifierId(syntheticId(tenantId + "|modifier|" + firstNonBlank(code, name)));
    row.setModifierName(name);
    row.setModifierCode(code);
    row.setModifierComment("modifier event");
    models.getModifiers().add(row);
  }

  private void handleUnitEvent(
      OpenLineageSourceModels models, long tenantId, OpenLineageEvent event) {
    Unit row = new Unit();
    String name = extractResourceName(event, tenantId, "unit", "unit_name");
    String code = extractFacetValue(event.getRawEvent(), "unitCode", "unit_code", "code");
    row.setUnitId(syntheticId(tenantId + "|unit|" + firstNonBlank(code, name)));
    row.setUnitName(name);
    row.setUnitCode(code);
    row.setUnitComment("unit event");
    models.getUnits().add(row);
  }

  private void handleValueDomainEvent(
      OpenLineageSourceModels models, long tenantId, OpenLineageEvent event) {
    ValueDomain row = new ValueDomain();
    String name = extractResourceName(event, tenantId, "valuedomain", "valuedomain_name");
    String code =
        extractFacetValue(event.getRawEvent(), "valuedomainCode", "valuedomain_code", "code");
    row.setDomainId(syntheticId(tenantId + "|valuedomain|" + firstNonBlank(code, name)));
    row.setDomainName(name);
    row.setDomainCode(code);
    row.setDomainComment("valuedomain event");
    models.getValueDomains().add(row);
  }

  private void addCatalog(
      OpenLineageSourceModels models, Long catalogId, String catalogName, String comment) {
    Catalog row = new Catalog();
    row.setCatalogId(catalogId);
    row.setCatalogName(catalogName);
    row.setCatalogComment(comment);
    models.getCatalogs().add(row);
  }

  private void addSchema(
      OpenLineageSourceModels models,
      Long schemaId,
      Long catalogId,
      String schemaName,
      String comment) {
    Schema row = new Schema();
    row.setSchemaId(schemaId);
    row.setCatalogId(catalogId);
    row.setSchemaName(schemaName);
    row.setSchemaComment(comment);
    models.getSchemas().add(row);
  }

  private void addTable(
      OpenLineageSourceModels models,
      Long tableId,
      Long catalogId,
      Long schemaId,
      String schemaName,
      String tableName,
      String comment) {
    Table row = new Table();
    row.setTableId(tableId);
    row.setCatalogId(catalogId);
    row.setSchemaId(schemaId);
    row.setSchemaName(schemaName);
    row.setTableName(tableName);
    row.setTableComment(comment);
    models.getTables().add(row);
  }

  private String resolveFamily(OpenLineageEvent event) {
    String jobName = trimToNull(event.jobName());
    if (jobName == null) {
      return "table";
    }
    String lower = jobName.toLowerCase();
    if (lower.contains("catalog")) {
      return "catalog";
    }
    if (lower.contains("schema")) {
      return "schema";
    }
    if (lower.contains("metric")) {
      return "metric";
    }
    if (lower.contains("tag")) {
      return "tag";
    }
    if (lower.contains("wordroot")) {
      return "wordroot";
    }
    if (lower.contains("modifier")) {
      return "modifier";
    }
    if (lower.contains("unit")) {
      return "unit";
    }
    if (lower.contains("valuedomain")) {
      return "valuedomain";
    }
    if (lower.contains("table")) {
      return "table";
    }
    return "table";
  }

  private List<DatasetRef> resolveDatasetRefs(OpenLineageEvent event, long tenantId) {
    Set<DatasetRef> refs = new LinkedHashSet<>();
    for (JsonNode node : event.datasetNodes()) {
      DatasetRef ref = toDatasetRef(node, tenantId);
      if (ref != null) {
        refs.add(ref);
      }
    }
    return new ArrayList<>(refs);
  }

  private DatasetRef toDatasetRef(JsonNode datasetNode, long tenantId) {
    if (datasetNode == null || !datasetNode.isObject()) {
      return null;
    }
    String namespace = trimToNull(datasetNode.path("namespace").asText(null));
    String name = trimToNull(datasetNode.path("name").asText(null));
    if (!StringUtils.hasText(name)) {
      return null;
    }
    String catalogName = parseCatalog(namespace);
    String[] schemaAndTable = splitSchemaAndTable(name);
    String schemaName = schemaAndTable[0];
    String tableName = schemaAndTable[1];
    Long catalogId = syntheticId(tenantId + "|catalog|" + catalogName);
    Long schemaId = syntheticId(tenantId + "|schema|" + catalogName + "|" + schemaName);
    Long tableId =
        syntheticId(tenantId + "|table|" + catalogName + "|" + schemaName + "|" + tableName);
    return new DatasetRef(catalogId, schemaId, tableId, catalogName, schemaName, tableName);
  }

  private String extractResourceName(
      OpenLineageEvent event, long tenantId, String family, String altKey) {
    String fromFacets =
        extractFacetValue(
            event.getRawEvent(), family + "Name", altKey, "name", family + "Code", "code");
    if (StringUtils.hasText(fromFacets)) {
      return fromFacets;
    }
    if (StringUtils.hasText(event.jobName())) {
      return event.jobName();
    }
    List<DatasetRef> refs = resolveDatasetRefs(event, tenantId);
    if (!refs.isEmpty()) {
      DatasetRef ref = refs.getFirst();
      return switch (family) {
        case "catalog" -> ref.catalogName();
        case "schema" -> ref.schemaName();
        case "table" -> ref.tableName();
        default -> family + "_" + syntheticId(event.getRawEvent().toString());
      };
    }
    return family + "_" + syntheticId(event.getRawEvent().toString());
  }

  private String extractFacetValue(JsonNode raw, String... keys) {
    JsonNode facets = raw.path("job").path("facets");
    if (!facets.isObject()) {
      return null;
    }
    for (String key : keys) {
      if (!StringUtils.hasText(key)) {
        continue;
      }
      JsonNode direct = facets.path(key);
      String directValue = extractText(direct);
      if (StringUtils.hasText(directValue)) {
        return directValue;
      }
      for (JsonNode child : iterable(facets)) {
        if (!child.isObject()) {
          continue;
        }
        String nested = extractText(child.path(key));
        if (StringUtils.hasText(nested)) {
          return nested;
        }
      }
    }
    return null;
  }

  private boolean hasFacet(JsonNode payload, String facetName) {
    for (JsonNode datasetNode : collectDatasets(payload.path("inputs"))) {
      if (datasetNode.path("facets").path(facetName).isObject()) {
        return true;
      }
    }
    for (JsonNode datasetNode : collectDatasets(payload.path("outputs"))) {
      if (datasetNode.path("facets").path(facetName).isObject()) {
        return true;
      }
    }
    JsonNode dataset = payload.path("dataset");
    return dataset.isObject() && dataset.path("facets").path(facetName).isObject();
  }

  private List<JsonNode> collectDatasets(JsonNode node) {
    if (node == null || !node.isArray()) {
      return List.of();
    }
    List<JsonNode> datasets = new ArrayList<>();
    node.forEach(datasets::add);
    return datasets;
  }

  private boolean contains(String value, String expectedPart) {
    return hasText(value)
        && expectedPart != null
        && value.toLowerCase().contains(expectedPart.toLowerCase());
  }

  private boolean startsWith(String value, String prefix) {
    return hasText(value) && prefix != null && value.toLowerCase().startsWith(prefix.toLowerCase());
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String parseCatalog(String namespace) {
    if (!StringUtils.hasText(namespace)) {
      return "default_catalog";
    }
    String normalized = namespace.trim();
    int protocol = normalized.indexOf("://");
    if (protocol >= 0 && protocol + 3 < normalized.length()) {
      normalized = normalized.substring(protocol + 3);
    }
    String[] parts = normalized.split("/");
    for (int i = parts.length - 1; i >= 0; i--) {
      if (StringUtils.hasText(parts[i])) {
        return parts[i].trim();
      }
    }
    return "default_catalog";
  }

  private String[] splitSchemaAndTable(String datasetName) {
    String normalized = datasetName.trim();
    int idx = normalized.lastIndexOf('.');
    if (idx > 0 && idx < normalized.length() - 1) {
      return new String[] {normalized.substring(0, idx), normalized.substring(idx + 1)};
    }
    return new String[] {"default_schema", normalized};
  }

  private Long syntheticId(String raw) {
    if (raw == null) {
      return 0L;
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
      long value = 0L;
      for (int i = 0; i < 8; i++) {
        value = (value << 8) | (hash[i] & 0xffL);
      }
      value = value & Long.MAX_VALUE;
      return value == 0L ? 1L : value;
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private String firstNonBlank(String first, String second) {
    String normalizedFirst = trimToNull(first);
    if (normalizedFirst != null) {
      return normalizedFirst;
    }
    String normalizedSecond = trimToNull(second);
    return normalizedSecond == null ? "unknown" : normalizedSecond;
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private String extractText(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    if (node.isTextual()) {
      return trimToNull(node.asText());
    }
    if (node.isNumber() || node.isBoolean()) {
      return node.asText();
    }
    return null;
  }

  private Iterable<JsonNode> iterable(JsonNode objectNode) {
    List<JsonNode> rows = new ArrayList<>();
    objectNode.elements().forEachRemaining(rows::add);
    return rows;
  }

  private record DatasetRef(
      Long catalogId,
      Long schemaId,
      Long tableId,
      String catalogName,
      String schemaName,
      String tableName) {}
}
