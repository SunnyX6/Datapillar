package com.sunny.datapillar.openlineage.sink;

import com.sunny.datapillar.openlineage.model.Catalog;
import com.sunny.datapillar.openlineage.model.Column;
import com.sunny.datapillar.openlineage.model.Metric;
import com.sunny.datapillar.openlineage.model.MetricVersion;
import com.sunny.datapillar.openlineage.model.Modifier;
import com.sunny.datapillar.openlineage.model.Schema;
import com.sunny.datapillar.openlineage.model.Table;
import com.sunny.datapillar.openlineage.model.Tag;
import com.sunny.datapillar.openlineage.model.TagRelation;
import com.sunny.datapillar.openlineage.model.Tenant;
import com.sunny.datapillar.openlineage.model.Unit;
import com.sunny.datapillar.openlineage.model.ValueDomain;
import com.sunny.datapillar.openlineage.model.WordRoot;
import com.sunny.datapillar.openlineage.pipeline.GraphRebuildStats;
import com.sunny.datapillar.openlineage.sink.dao.GraphDao;
import com.sunny.datapillar.openlineage.source.OpenLineageSourceModels;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTaskPayload;
import com.sunny.datapillar.openlineage.source.event.EmbeddingTriggerType;
import com.sunny.datapillar.openlineage.web.mapper.EmbeddingBindingMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Graph sink entrypoint for writing parsed current models to Neo4j. */
@Component
public class GraphSink {

  private final GraphDao graphDao;

  public GraphSink(GraphDao graphDao) {
    this.graphDao = graphDao;
  }

  public List<EmbeddingTaskPayload> apply(
      Tenant tenant,
      OpenLineageSourceModels models,
      EmbeddingBindingMapper.RuntimeModelRow runtime,
      Long targetRevision,
      EmbeddingTriggerType trigger,
      String sourceEventMessageId) {
    GraphWritePlan plan =
        buildWritePlan(
            tenant.getTenantId(),
            models.getCatalogs(),
            models.getSchemas(),
            models.getTables(),
            models.getColumns(),
            models.getMetrics(),
            models.getMetricVersions(),
            models.getTags(),
            models.getTagRelations(),
            models.getWordRoots(),
            models.getModifiers(),
            models.getUnits(),
            models.getValueDomains(),
            tenant.getTenantCode(),
            runtime,
            targetRevision,
            trigger,
            sourceEventMessageId);
    graphDao.writeGraph(tenant, plan.nodes(), plan.links());
    return plan.embeddingTasks();
  }

  public GraphRebuildStats rebuildFromMetadata(
      Tenant tenant,
      EmbeddingBindingMapper.RuntimeModelRow runtime,
      Long targetRevision,
      EmbeddingTriggerType trigger,
      String sourceEventMessageId,
      List<Catalog> catalogs,
      List<Schema> schemas,
      List<Table> tables,
      List<Column> columns,
      List<Metric> metrics,
      List<MetricVersion> metricVersions,
      List<Tag> tags,
      List<TagRelation> tagRelations,
      List<WordRoot> wordRoots,
      List<Modifier> modifiers,
      List<Unit> units,
      List<ValueDomain> valueDomains) {
    GraphWritePlan plan =
        buildWritePlan(
            tenant.getTenantId(),
            catalogs,
            schemas,
            tables,
            columns,
            metrics,
            metricVersions,
            tags,
            tagRelations,
            wordRoots,
            modifiers,
            units,
            valueDomains,
            tenant.getTenantCode(),
            runtime,
            targetRevision,
            trigger,
            sourceEventMessageId);
    graphDao.writeGraph(tenant, plan.nodes(), plan.links());
    return new GraphRebuildStats(plan.graphUpserts(), plan.embeddingTasks());
  }

  private GraphWritePlan buildWritePlan(
      Long tenantId,
      List<Catalog> catalogs,
      List<Schema> schemas,
      List<Table> tables,
      List<Column> columns,
      List<Metric> metrics,
      List<MetricVersion> metricVersions,
      List<Tag> tags,
      List<TagRelation> tagRelations,
      List<WordRoot> wordRoots,
      List<Modifier> modifiers,
      List<Unit> units,
      List<ValueDomain> valueDomains,
      String tenantCode,
      EmbeddingBindingMapper.RuntimeModelRow runtime,
      Long targetRevision,
      EmbeddingTriggerType trigger,
      String sourceEventMessageId) {
    EmbeddingTaskContext context =
        new EmbeddingTaskContext(
            tenantId, tenantCode, runtime, targetRevision, trigger, sourceEventMessageId);
    LinkedHashMap<String, GraphDao.NodeWrite> nodes = new LinkedHashMap<>();
    LinkedHashMap<String, GraphDao.LinkWrite> links = new LinkedHashMap<>();
    LinkedHashMap<String, EmbeddingTaskPayload> targets = new LinkedHashMap<>();

    Map<Long, String> catalogIds = new LinkedHashMap<>();
    for (Catalog row : safe(catalogs)) {
      String nodeId = catalogNodeId(tenantId, row);
      putIfPositive(catalogIds, row.getCatalogId(), nodeId);
      addNode(
          nodes,
          "Catalog",
          nodeId,
          nonBlank(row.getCatalogName(), "catalog_" + row.getCatalogId()),
          row.getCatalogComment(),
          properties("catalogId", row.getCatalogId()));
      addTarget(
          targets,
          context,
          nodeId,
          "Catalog",
          buildEmbeddingText(row.getCatalogName(), row.getCatalogComment()));
    }

    Map<Long, String> schemaIds = new LinkedHashMap<>();
    for (Schema row : safe(schemas)) {
      String nodeId = schemaNodeId(tenantId, row);
      putIfPositive(schemaIds, row.getSchemaId(), nodeId);
      addNode(
          nodes,
          "Schema",
          nodeId,
          nonBlank(row.getSchemaName(), "schema_" + row.getSchemaId()),
          row.getSchemaComment(),
          properties("schemaId", row.getSchemaId()));
      addLink(links, catalogIds.get(row.getCatalogId()), nodeId, "HAS_SCHEMA");
      addTarget(
          targets,
          context,
          nodeId,
          "Schema",
          buildEmbeddingText(row.getSchemaName(), row.getSchemaComment()));
    }

    Map<Long, String> tableIds = new LinkedHashMap<>();
    for (Table row : safe(tables)) {
      String nodeId = tableNodeId(tenantId, row);
      putIfPositive(tableIds, row.getTableId(), nodeId);
      addNode(
          nodes,
          "Table",
          nodeId,
          nonBlank(row.getTableName(), "table_" + row.getTableId()),
          row.getTableComment(),
          properties("tableId", row.getTableId()));
      addLink(links, schemaIds.get(row.getSchemaId()), nodeId, "HAS_TABLE");
      addTarget(
          targets,
          context,
          nodeId,
          "Table",
          buildEmbeddingText(row.getTableName(), row.getTableComment()));
    }

    Map<Long, String> columnIds = new LinkedHashMap<>();
    for (Column row : safe(columns)) {
      String nodeId = columnNodeId(tenantId, row);
      putIfPositive(columnIds, row.getColumnId(), nodeId);
      addNode(
          nodes,
          "Column",
          nodeId,
          nonBlank(row.getColumnName(), "column_" + row.getColumnId()),
          row.getColumnComment(),
          properties("columnId", row.getColumnId(), "columnType", row.getColumnType()));
      addLink(links, tableIds.get(row.getTableId()), nodeId, "HAS_COLUMN");
      addTarget(
          targets,
          context,
          nodeId,
          "Column",
          buildEmbeddingText(row.getColumnName(), row.getColumnType(), row.getColumnComment()));
    }

    Map<Long, String> metricIds = new LinkedHashMap<>();
    for (Metric row : safe(metrics)) {
      String label = resolveMetricLabel(row.getMetricType());
      String nodeId = metricNodeId(tenantId, row);
      putIfPositive(metricIds, row.getMetricId(), nodeId);
      addNode(
          nodes,
          label,
          nodeId,
          nonBlank(row.getMetricName(), "metric_" + row.getMetricId()),
          row.getMetricComment(),
          properties(
              "metricId", row.getMetricId(),
              "metricCode", row.getMetricCode(),
              "metricType", row.getMetricType()));
      addLink(links, schemaIds.get(row.getSchemaId()), nodeId, "HAS_METRIC");
      addTarget(
          targets,
          context,
          nodeId,
          label,
          buildEmbeddingText(row.getMetricName(), row.getMetricCode(), row.getMetricComment()));
    }

    for (MetricVersion row : safe(metricVersions)) {
      String metricNodeId = metricIds.get(row.getMetricId());
      if (metricNodeId == null) {
        continue;
      }
      for (Long columnId : parseCsvLongs(row.getMeasureColumnIds())) {
        addLink(links, metricNodeId, columnIds.get(columnId), "MEASURES");
      }
      for (Long columnId : parseCsvLongs(row.getFilterColumnIds())) {
        addLink(links, metricNodeId, columnIds.get(columnId), "FILTERS_BY");
      }
      for (Long parentMetricId : parseCsvLongs(row.getParentMetricCodes())) {
        addLink(links, metricNodeId, metricIds.get(parentMetricId), "DERIVED_FROM");
      }
    }

    Map<Long, String> tagIds = new LinkedHashMap<>();
    for (Tag row : safe(tags)) {
      String nodeId = tagNodeId(tenantId, row);
      putIfPositive(tagIds, row.getTagId(), nodeId);
      addNode(
          nodes,
          "Tag",
          nodeId,
          nonBlank(row.getTagName(), "tag_" + row.getTagId()),
          row.getComment(),
          properties("tagId", row.getTagId()));
      addTarget(
          targets, context, nodeId, "Tag", buildEmbeddingText(row.getTagName(), row.getComment()));
    }

    for (TagRelation row : safe(tagRelations)) {
      String tagNodeId = tagIds.get(row.getTagId());
      String targetNodeId =
          resolveTagTargetId(
              row.getMetadataObjectType(),
              row.getMetadataObjectId(),
              catalogIds,
              schemaIds,
              tableIds,
              columnIds);
      addLink(links, targetNodeId, tagNodeId, "HAS_TAG");
    }

    rebuildSimpleNodes(context, safe(wordRoots), nodes, targets);
    rebuildSimpleNodes(context, safe(modifiers), nodes, targets);
    rebuildSimpleNodes(context, safe(units), nodes, targets);
    rebuildSimpleNodes(context, safe(valueDomains), nodes, targets);

    return new GraphWritePlan(
        new ArrayList<>(nodes.values()),
        new ArrayList<>(links.values()),
        new ArrayList<>(targets.values()),
        nodes.size());
  }

  private void rebuildSimpleNodes(
      EmbeddingTaskContext context,
      List<?> rows,
      LinkedHashMap<String, GraphDao.NodeWrite> nodes,
      LinkedHashMap<String, EmbeddingTaskPayload> targets) {
    Long tenantId = context.tenantId();
    for (Object row : safe(rows)) {
      if (row instanceof WordRoot wordRootRow) {
        String nodeId = wordRootNodeId(tenantId, wordRootRow);
        addNode(
            nodes,
            "WordRoot",
            nodeId,
            nonBlank(wordRootRow.getRootName(), "wordroot_" + wordRootRow.getRootId()),
            wordRootRow.getRootComment(),
            properties("rootId", wordRootRow.getRootId(), "rootCode", wordRootRow.getRootCode()));
        addTarget(
            targets,
            context,
            nodeId,
            "WordRoot",
            buildEmbeddingText(
                wordRootRow.getRootName(),
                wordRootRow.getRootCode(),
                wordRootRow.getRootComment()));
      } else if (row instanceof Modifier modifierRow) {
        String nodeId = modifierNodeId(tenantId, modifierRow);
        addNode(
            nodes,
            "Modifier",
            nodeId,
            nonBlank(modifierRow.getModifierName(), "modifier_" + modifierRow.getModifierId()),
            modifierRow.getModifierComment(),
            properties(
                "modifierId", modifierRow.getModifierId(),
                "modifierCode", modifierRow.getModifierCode()));
        addTarget(
            targets,
            context,
            nodeId,
            "Modifier",
            buildEmbeddingText(
                modifierRow.getModifierName(),
                modifierRow.getModifierCode(),
                modifierRow.getModifierComment()));
      } else if (row instanceof Unit unitRow) {
        String nodeId = unitNodeId(tenantId, unitRow);
        addNode(
            nodes,
            "Unit",
            nodeId,
            nonBlank(unitRow.getUnitName(), "unit_" + unitRow.getUnitId()),
            unitRow.getUnitComment(),
            properties("unitId", unitRow.getUnitId(), "unitCode", unitRow.getUnitCode()));
        addTarget(
            targets,
            context,
            nodeId,
            "Unit",
            buildEmbeddingText(
                unitRow.getUnitName(), unitRow.getUnitCode(), unitRow.getUnitComment()));
      } else if (row instanceof ValueDomain valueDomainRow) {
        String nodeId = valueDomainNodeId(tenantId, valueDomainRow);
        addNode(
            nodes,
            "ValueDomain",
            nodeId,
            nonBlank(valueDomainRow.getDomainName(), "valuedomain_" + valueDomainRow.getDomainId()),
            valueDomainRow.getDomainComment(),
            properties(
                "valueDomainId", valueDomainRow.getDomainId(),
                "valueDomainCode", valueDomainRow.getDomainCode()));
        addTarget(
            targets,
            context,
            nodeId,
            "ValueDomain",
            buildEmbeddingText(
                valueDomainRow.getDomainName(),
                valueDomainRow.getDomainCode(),
                valueDomainRow.getDomainComment()));
      }
    }
  }

  private String resolveTagTargetId(
      String metadataObjectType,
      Long metadataObjectId,
      Map<Long, String> catalogIds,
      Map<Long, String> schemaIds,
      Map<Long, String> tableIds,
      Map<Long, String> columnIds) {
    if (metadataObjectId == null || !StringUtils.hasText(metadataObjectType)) {
      return null;
    }
    String type = metadataObjectType.trim().toUpperCase();
    return switch (type) {
      case "CATALOG" -> catalogIds.get(metadataObjectId);
      case "SCHEMA" -> schemaIds.get(metadataObjectId);
      case "TABLE" -> tableIds.get(metadataObjectId);
      case "COLUMN" -> columnIds.get(metadataObjectId);
      default -> null;
    };
  }

  private void addNode(
      LinkedHashMap<String, GraphDao.NodeWrite> nodes,
      String label,
      String nodeId,
      String name,
      String description,
      Map<String, Object> extraProperties) {
    if (!StringUtils.hasText(nodeId)) {
      return;
    }
    nodes.putIfAbsent(
        nodeId, new GraphDao.NodeWrite(label, nodeId, name, description, extraProperties));
  }

  private void addLink(
      LinkedHashMap<String, GraphDao.LinkWrite> links,
      String fromId,
      String toId,
      String relationshipType) {
    if (!StringUtils.hasText(fromId)
        || !StringUtils.hasText(toId)
        || !StringUtils.hasText(relationshipType)) {
      return;
    }
    String key = fromId + "|" + relationshipType + "|" + toId;
    links.putIfAbsent(key, new GraphDao.LinkWrite(fromId, toId, relationshipType));
  }

  private void addTarget(
      LinkedHashMap<String, EmbeddingTaskPayload> targets,
      EmbeddingTaskContext context,
      String resourceId,
      String resourceType,
      String content) {
    if (!StringUtils.hasText(resourceId)) {
      return;
    }
    EmbeddingTaskPayload task = new EmbeddingTaskPayload();
    task.setTenantId(context.tenantId());
    task.setTenantCode(context.tenantCode());
    task.setResourceId(resourceId);
    task.setResourceType(resourceType);
    task.setContent(nonBlank(content, resourceId));
    task.setTargetRevision(context.targetRevision());
    task.setTrigger(context.trigger());
    task.setSourceEventMessageId(context.sourceEventMessageId());
    task.setAiModelId(context.runtime().getAiModelId());
    task.setProviderCode(context.runtime().getProviderCode());
    task.setProviderModelId(context.runtime().getProviderModelId());
    task.setEmbeddingDimension(context.runtime().getEmbeddingDimension());
    task.setBaseUrl(context.runtime().getBaseUrl());
    task.setApiKeyCiphertext(context.runtime().getApiKey());
    targets.putIfAbsent(resourceId, task);
  }

  private void putIfPositive(Map<Long, String> ids, Long id, String nodeId) {
    if (id != null && id > 0 && StringUtils.hasText(nodeId)) {
      ids.put(id, nodeId);
    }
  }

  private Map<String, Object> properties(Object... values) {
    Map<String, Object> properties = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) {
      Object key = values[i];
      Object value = values[i + 1];
      if (key instanceof String textKey && value != null) {
        properties.put(textKey, value);
      }
    }
    return properties;
  }

  private String catalogNodeId(Long tenantId, Catalog row) {
    return stableId(
        tenantId + "|catalog|" + idOrName(row.getCatalogId(), row.getCatalogName(), "catalog"));
  }

  private String schemaNodeId(Long tenantId, Schema row) {
    return stableId(
        tenantId + "|schema|" + idOrName(row.getSchemaId(), row.getSchemaName(), "schema"));
  }

  private String tableNodeId(Long tenantId, Table row) {
    return stableId(tenantId + "|table|" + idOrName(row.getTableId(), row.getTableName(), "table"));
  }

  private String columnNodeId(Long tenantId, Column row) {
    return stableId(
        tenantId + "|column|" + idOrName(row.getColumnId(), row.getColumnName(), "column"));
  }

  private String metricNodeId(Long tenantId, Metric row) {
    return stableId(
        tenantId + "|metric|" + idOrName(row.getMetricId(), row.getMetricName(), "metric"));
  }

  private String tagNodeId(Long tenantId, Tag row) {
    return stableId(tenantId + "|tag|" + idOrName(row.getTagId(), row.getTagName(), "tag"));
  }

  private String wordRootNodeId(Long tenantId, WordRoot row) {
    return stableId(
        tenantId + "|wordroot|" + idOrName(row.getRootId(), row.getRootName(), "wordroot"));
  }

  private String modifierNodeId(Long tenantId, Modifier row) {
    return stableId(
        tenantId + "|modifier|" + idOrName(row.getModifierId(), row.getModifierName(), "modifier"));
  }

  private String unitNodeId(Long tenantId, Unit row) {
    return stableId(tenantId + "|unit|" + idOrName(row.getUnitId(), row.getUnitName(), "unit"));
  }

  private String valueDomainNodeId(Long tenantId, ValueDomain row) {
    return stableId(
        tenantId
            + "|valuedomain|"
            + idOrName(row.getDomainId(), row.getDomainName(), "valuedomain"));
  }

  private String idOrName(Long id, String name, String fallback) {
    if (id != null && id > 0) {
      return String.valueOf(id);
    }
    return nonBlank(name, fallback);
  }

  private String resolveMetricLabel(String metricType) {
    String normalized = trimToNull(metricType);
    if (normalized == null) {
      return "AtomicMetric";
    }
    String upper = normalized.toUpperCase();
    if (upper.contains("DERIVED")) {
      return "DerivedMetric";
    }
    if (upper.contains("COMPOSITE")) {
      return "CompositeMetric";
    }
    return "AtomicMetric";
  }

  private List<Long> parseCsvLongs(String raw) {
    String normalized = trimToNull(raw);
    if (normalized == null) {
      return List.of();
    }
    List<Long> values = new ArrayList<>();
    for (String token : normalized.split(",")) {
      String text = trimToNull(token);
      if (text == null) {
        continue;
      }
      try {
        values.add(Long.parseLong(text));
      } catch (NumberFormatException ignored) {
        // Ignore malformed id token.
      }
    }
    return values;
  }

  private String buildEmbeddingText(String... values) {
    StringBuilder builder = new StringBuilder();
    for (String value : values) {
      if (!StringUtils.hasText(value)) {
        continue;
      }
      if (builder.length() > 0) {
        builder.append(' ');
      }
      builder.append(value.trim());
    }
    return builder.length() == 0 ? "" : builder.toString();
  }

  private String stableId(String raw) {
    if (raw == null) {
      return "";
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(24);
      for (int i = 0; i < 12; i++) {
        builder.append(String.format("%02x", hash[i]));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private String nonBlank(String value, String fallback) {
    return StringUtils.hasText(value) ? value.trim() : fallback;
  }

  private String trimToNull(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    return value.trim();
  }

  private <T> List<T> safe(List<T> rows) {
    return rows == null ? List.of() : rows;
  }

  private record EmbeddingTaskContext(
      Long tenantId,
      String tenantCode,
      EmbeddingBindingMapper.RuntimeModelRow runtime,
      Long targetRevision,
      EmbeddingTriggerType trigger,
      String sourceEventMessageId) {}

  private record GraphWritePlan(
      List<GraphDao.NodeWrite> nodes,
      List<GraphDao.LinkWrite> links,
      List<EmbeddingTaskPayload> embeddingTasks,
      int graphUpserts) {}
}
