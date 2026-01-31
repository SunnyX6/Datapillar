/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.gravitino.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.json.JsonUtils;
import org.apache.gravitino.listener.api.event.AlterMetricEvent;
import org.apache.gravitino.listener.api.info.MetricInfo;
import org.apache.gravitino.meta.MetricEntity;
import org.apache.gravitino.meta.MetricVersionEntity;
import org.apache.gravitino.storage.relational.po.ColumnPO;
import org.apache.gravitino.storage.relational.po.TablePO;
import org.apache.gravitino.storage.relational.service.MetricMetaService;
import org.apache.gravitino.storage.relational.service.MetricVersionMetaService;
import org.apache.gravitino.storage.relational.service.SchemaMetaService;
import org.apache.gravitino.storage.relational.service.TableColumnMetaService;
import org.apache.gravitino.storage.relational.service.TableMetaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 指标血缘级联事件发射器
 *
 * <p>当表/Schema/Catalog 发生变更时，主动发送指标变更事件，确保指标引用表/列为最新。
 */
public final class MetricLineageEventEmitter {

  private static final Logger LOG = LoggerFactory.getLogger(MetricLineageEventEmitter.class);

  private MetricLineageEventEmitter() {}

  public static void emitForRefTable(EventBus eventBus, String user, Long tableId) {
    if (tableId == null) {
      return;
    }

    List<MetricVersionEntity> versions =
        MetricVersionMetaService.getInstance().listCurrentVersionsByRefTableId(tableId);
    if (versions.isEmpty()) {
      return;
    }

    Map<Long, String> columnNameMap = loadColumnNameMap(tableId);
    for (MetricVersionEntity version : versions) {
      MetricInfo info = buildMetricInfo(version, columnNameMap);
      eventBus.dispatchEvent(new AlterMetricEvent(user, version.metricIdentifier(), info));
    }
  }

  public static void emitForSchema(EventBus eventBus, String user, NameIdentifier schemaIdent) {
    Namespace schemaNs = toSchemaNamespace(schemaIdent);
    List<MetricEntity> metrics = MetricMetaService.getInstance().listMetricsByNamespace(schemaNs);
    if (metrics.isEmpty()) {
      return;
    }

    Map<Long, Map<Long, String>> columnNameCache = new HashMap<>();

    for (MetricEntity metric : metrics) {
      Integer currentVersion = metric.currentVersion();
      if (currentVersion == null) {
        continue;
      }

      MetricVersionEntity version;
      try {
        version =
            MetricVersionMetaService.getInstance()
                .getVersionByIdentifier(metric.nameIdentifier(), currentVersion);
      } catch (Exception e) {
        LOG.warn("指标版本读取失败，跳过级联事件: metric={} version={}", metric.code(), currentVersion);
        continue;
      }

      Map<Long, String> columnNameMap =
          getColumnNameMap(columnNameCache, version.refTableId());
      MetricInfo info = buildMetricInfo(version, columnNameMap);
      eventBus.dispatchEvent(new AlterMetricEvent(user, metric.nameIdentifier(), info));
    }
  }

  public static void emitForCatalog(EventBus eventBus, String user, NameIdentifier catalogIdent) {
    String[] levels = catalogIdent.namespace().levels();
    if (levels.length == 0) {
      return;
    }
    Namespace catalogNs = Namespace.of(levels[0], catalogIdent.name());
    SchemaMetaService.getInstance()
        .listSchemasByNamespace(catalogNs)
        .forEach(schema -> emitForSchema(eventBus, user, schema.nameIdentifier()));
  }

  private static Namespace toSchemaNamespace(NameIdentifier schemaIdent) {
    String[] levels = schemaIdent.namespace().levels();
    String[] schemaLevels = new String[levels.length + 1];
    System.arraycopy(levels, 0, schemaLevels, 0, levels.length);
    schemaLevels[levels.length] = schemaIdent.name();
    return Namespace.of(schemaLevels);
  }

  private static Map<Long, String> getColumnNameMap(
      Map<Long, Map<Long, String>> cache, Long tableId) {
    if (tableId == null) {
      return Collections.emptyMap();
    }
    return cache.computeIfAbsent(tableId, MetricLineageEventEmitter::loadColumnNameMap);
  }

  private static Map<Long, String> loadColumnNameMap(Long tableId) {
    if (tableId == null) {
      return Collections.emptyMap();
    }
    TablePO tablePO = TableMetaService.getInstance().getTablePOById(tableId);
    if (tablePO == null || tablePO.getCurrentVersion() == null) {
      return Collections.emptyMap();
    }

    List<ColumnPO> columns =
        TableColumnMetaService.getInstance()
            .getColumnsByTableIdAndVersion(tableId, tablePO.getCurrentVersion());
    if (columns.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<Long, String> map = new HashMap<>(columns.size());
    for (ColumnPO column : columns) {
      map.put(column.getColumnId(), column.getColumnName());
    }
    return map;
  }

  private static MetricInfo buildMetricInfo(
      MetricVersionEntity version, Map<Long, String> columnNameMap) {
    String measureColumns =
        buildColumnNamesJson(
            version.measureColumnIds(), columnNameMap, version.metricCode(), "measure");
    String filterColumns =
        buildColumnNamesJson(
            version.filterColumnIds(), columnNameMap, version.metricCode(), "filter");

    return new MetricInfo(
        version.metricName(),
        version.metricCode(),
        version.metricType(),
        Collections.emptyMap(),
        version.comment(),
        version.auditInfo(),
        null,
        null,
        version.unit(),
        version.calculationFormula(),
        version.parentMetricCodes(),
        version.refCatalogName(),
        version.refSchemaName(),
        version.refTableName(),
        measureColumns,
        filterColumns);
  }

  private static String buildColumnNamesJson(
      String rawIds, Map<Long, String> columnNameMap, String metricCode, String fieldType) {
    List<Long> columnIds = parseColumnIds(rawIds, metricCode, fieldType);
    if (columnIds.isEmpty()) {
      return "[]";
    }

    List<Map<String, String>> columns = new ArrayList<>();
    for (Long columnId : columnIds) {
      String columnName = columnNameMap.get(columnId);
      if (columnName != null && !columnName.isBlank()) {
        Map<String, String> item = new HashMap<>(1);
        item.put("name", columnName);
        columns.add(item);
      }
    }

    try {
      return JsonUtils.anyFieldMapper().writeValueAsString(columns);
    } catch (JsonProcessingException e) {
      LOG.warn("指标列序列化失败，使用空列表: metric={} field={}", metricCode, fieldType);
      return "[]";
    }
  }

  private static List<Long> parseColumnIds(String rawIds, String metricCode, String fieldType) {
    if (rawIds == null || rawIds.isBlank()) {
      return Collections.emptyList();
    }

    try {
      List<?> values = JsonUtils.anyFieldMapper().readValue(rawIds, List.class);
      if (values == null || values.isEmpty()) {
        return Collections.emptyList();
      }

      List<Long> ids = new ArrayList<>(values.size());
      for (Object value : values) {
        if (value instanceof Number) {
          ids.add(((Number) value).longValue());
        } else if (value instanceof String) {
          String str = ((String) value).trim();
          if (!str.isEmpty()) {
            try {
              ids.add(Long.parseLong(str));
            } catch (NumberFormatException e) {
              LOG.warn(
                  "指标列 ID 解析失败，跳过: metric={} field={} value={}",
                  metricCode,
                  fieldType,
                  str);
            }
          }
        }
      }
      return ids;
    } catch (Exception e) {
      LOG.warn("指标列 ID JSON 解析失败，跳过: metric={} field={}", metricCode, fieldType);
      return Collections.emptyList();
    }
  }
}
