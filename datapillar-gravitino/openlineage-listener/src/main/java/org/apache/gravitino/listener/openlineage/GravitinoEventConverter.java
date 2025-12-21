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

package org.apache.gravitino.listener.openlineage;

import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineage.DatasetFacets;
import io.openlineage.client.OpenLineage.DatasetFacetsBuilder;
import io.openlineage.client.OpenLineage.InputDataset;
import io.openlineage.client.OpenLineage.Job;
import io.openlineage.client.OpenLineage.OutputDataset;
import io.openlineage.client.OpenLineage.Run;
import io.openlineage.client.OpenLineage.RunEvent;
import io.openlineage.client.OpenLineage.SchemaDatasetFacet;
import io.openlineage.client.OpenLineage.SchemaDatasetFacetFields;
import java.net.URI;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.gravitino.Audit;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.listener.api.event.AlterTableEvent;
import org.apache.gravitino.listener.api.event.CreateCatalogEvent;
import org.apache.gravitino.listener.api.event.CreateSchemaEvent;
import org.apache.gravitino.listener.api.event.CreateTableEvent;
import org.apache.gravitino.listener.api.event.CreateWordRootEvent;
import org.apache.gravitino.listener.api.event.DeleteMetricEvent;
import org.apache.gravitino.listener.api.event.DeleteWordRootEvent;
import org.apache.gravitino.listener.api.event.DropCatalogEvent;
import org.apache.gravitino.listener.api.event.DropSchemaEvent;
import org.apache.gravitino.listener.api.event.DropTableEvent;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.api.event.LoadSchemaEvent;
import org.apache.gravitino.listener.api.event.LoadTableEvent;
import org.apache.gravitino.listener.api.event.RegisterMetricEvent;
import org.apache.gravitino.listener.api.info.MetricInfo;
import org.apache.gravitino.listener.api.info.SchemaInfo;
import org.apache.gravitino.listener.api.info.TableInfo;
import org.apache.gravitino.listener.api.info.WordRootInfo;
import org.apache.gravitino.listener.openlineage.facets.GravitinoDatasetFacet;
import org.apache.gravitino.listener.openlineage.facets.GravitinoDatasetFacet.GravitinoColumnMetadata;
import org.apache.gravitino.rel.Column;
import org.apache.gravitino.rel.expressions.Expression;

/**
 * Converts Gravitino events to OpenLineage RunEvents.
 *
 * <p>Mapping:
 *
 * <ul>
 *   <li>Gravitino Table/Schema/Catalog → OpenLineage Dataset
 *   <li>Gravitino Event (CREATE/ALTER/DROP) → OpenLineage RunEvent
 *   <li>Gravitino user + operation → OpenLineage Job
 * </ul>
 */
@Slf4j
public class GravitinoEventConverter {

  private static final String GRAVITINO_FACET_KEY = "gravitino";

  private final OpenLineage openLineage;
  private final String namespace;
  private final URI producerUri;

  public GravitinoEventConverter(OpenLineage openLineage, String namespace) {
    this.openLineage = openLineage;
    this.namespace = namespace;
    this.producerUri = URI.create("https://github.com/apache/gravitino");
  }

  /**
   * Convert Gravitino event to OpenLineage RunEvent.
   *
   * @param event Gravitino event
   * @return OpenLineage RunEvent, or null if event type is not supported
   */
  public RunEvent convert(Event event) {
    if (event instanceof CreateTableEvent) {
      return convertCreateTable((CreateTableEvent) event);
    } else if (event instanceof AlterTableEvent) {
      return convertAlterTable((AlterTableEvent) event);
    } else if (event instanceof DropTableEvent) {
      return convertDropTable((DropTableEvent) event);
    } else if (event instanceof LoadTableEvent) {
      return convertLoadTable((LoadTableEvent) event);
    } else if (event instanceof CreateSchemaEvent) {
      return convertCreateSchema((CreateSchemaEvent) event);
    } else if (event instanceof DropSchemaEvent) {
      return convertDropSchema((DropSchemaEvent) event);
    } else if (event instanceof LoadSchemaEvent) {
      return convertLoadSchema((LoadSchemaEvent) event);
    } else if (event instanceof CreateCatalogEvent) {
      return convertCreateCatalog((CreateCatalogEvent) event);
    } else if (event instanceof DropCatalogEvent) {
      return convertDropCatalog((DropCatalogEvent) event);
    } else if (event instanceof RegisterMetricEvent) {
      return convertRegisterMetric((RegisterMetricEvent) event);
    } else if (event instanceof DeleteMetricEvent) {
      return convertDeleteMetric((DeleteMetricEvent) event);
    } else if (event instanceof CreateWordRootEvent) {
      return convertCreateWordRoot((CreateWordRootEvent) event);
    } else if (event instanceof DeleteWordRootEvent) {
      return convertDeleteWordRoot((DeleteWordRootEvent) event);
    }

    log.debug("Unsupported event type: {}", event.getClass().getSimpleName());
    return null;
  }

  private RunEvent convertCreateTable(CreateTableEvent event) {
    TableInfo tableInfo = event.createdTableInfo();
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset = createTableDataset(identifier, tableInfo);

    return createRunEvent(
        event,
        "gravitino.create_table",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertAlterTable(AlterTableEvent event) {
    TableInfo tableInfo = event.updatedTableInfo();
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset = createTableDataset(identifier, tableInfo);

    return createRunEvent(
        event,
        "gravitino.alter_table",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropTable(DropTableEvent event) {
    NameIdentifier identifier = event.identifier();

    // For drop, we create a minimal dataset without schema
    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.DROP,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.drop_table",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  /**
   * 转换 LoadTableEvent（元数据同步事件）。
   *
   * <p>当用户在 UI 点击表时，Gravitino 会从底层数据源同步元数据到自己的库，触发此事件。
   */
  private RunEvent convertLoadTable(LoadTableEvent event) {
    TableInfo tableInfo = event.loadedTableInfo();
    NameIdentifier identifier = event.identifier();

    // Load 事件作为 InputDataset（读取操作）
    InputDataset inputDataset = createTableInputDataset(identifier, tableInfo);

    return createRunEvent(
        event,
        "gravitino.load_table",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.singletonList(inputDataset),
        Collections.emptyList());
  }

  private InputDataset createTableInputDataset(NameIdentifier identifier, TableInfo tableInfo) {
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    List<GravitinoColumnMetadata> columnMetadataList = new ArrayList<>();

    if (tableInfo != null && tableInfo.columns() != null) {
      for (Column column : tableInfo.columns()) {
        fields.add(
            openLineage.newSchemaDatasetFacetFields(
                column.name(), column.dataType().simpleString(), column.comment(), null));

        // 收集列扩展元数据
        columnMetadataList.add(
            GravitinoColumnMetadata.builder()
                .name(column.name())
                .nullable(column.nullable())
                .autoIncrement(column.autoIncrement())
                .defaultValue(formatDefaultValue(column.defaultValue()))
                .build());
      }
    }

    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    // 构建 Gravitino 自定义 facet
    GravitinoDatasetFacet gravitinoFacet = buildGravitinoFacet(tableInfo, columnMetadataList);

    DatasetFacetsBuilder facetsBuilder = openLineage.newDatasetFacetsBuilder().schema(schemaFacet);

    // 添加自定义 facet
    if (gravitinoFacet != null) {
      facetsBuilder.put(GRAVITINO_FACET_KEY, gravitinoFacet);
    }

    return openLineage
        .newInputDatasetBuilder()
        .namespace(formatDatasetNamespace(identifier))
        .name(formatDatasetName(identifier))
        .facets(facetsBuilder.build())
        .build();
  }

  private RunEvent convertCreateSchema(CreateSchemaEvent event) {
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(identifier.name())
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange
                                .CREATE,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.create_schema",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropSchema(DropSchemaEvent event) {
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(identifier.name())
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.DROP,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.drop_schema",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  /**
   * 转换 LoadSchemaEvent（Schema 元数据同步事件）。
   *
   * <p>当用户在 UI 点击 Schema 时，Gravitino 会从底层数据源同步元数据到自己的库，触发此事件。
   */
  private RunEvent convertLoadSchema(LoadSchemaEvent event) {
    NameIdentifier identifier = event.identifier();
    SchemaInfo schemaInfo = event.loadedSchemaInfo();

    // 构建 Gravitino 自定义 facet for Schema
    GravitinoDatasetFacet gravitinoFacet = buildSchemaGravitinoFacet(schemaInfo);

    DatasetFacetsBuilder facetsBuilder =
        openLineage
            .newDatasetFacetsBuilder()
            .documentation(
                openLineage.newDocumentationDatasetFacet(
                    schemaInfo != null ? schemaInfo.comment() : null));

    if (gravitinoFacet != null) {
      facetsBuilder.put(GRAVITINO_FACET_KEY, gravitinoFacet);
    }

    // Load 事件作为 InputDataset（读取操作）
    InputDataset inputDataset =
        openLineage
            .newInputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(identifier.name())
            .facets(facetsBuilder.build())
            .build();

    return createRunEvent(
        event,
        "gravitino.load_schema",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.singletonList(inputDataset),
        Collections.emptyList());
  }

  /** 构建 Schema 的 Gravitino 自定义 facet */
  private GravitinoDatasetFacet buildSchemaGravitinoFacet(SchemaInfo schemaInfo) {
    if (schemaInfo == null) {
      return null;
    }

    GravitinoDatasetFacet.GravitinoDatasetFacetBuilder builder =
        GravitinoDatasetFacet.builder(producerUri)
            .description(schemaInfo.comment())
            .properties(schemaInfo.properties());

    // 审计信息
    Audit audit = schemaInfo.audit();
    if (audit != null) {
      GravitinoDatasetFacet.fromAudit(builder, audit);
    }

    return builder.build();
  }

  private RunEvent convertCreateCatalog(CreateCatalogEvent event) {
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(namespace)
            .name(identifier.name())
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange
                                .CREATE,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.create_catalog",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropCatalog(DropCatalogEvent event) {
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(namespace)
            .name(identifier.name())
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.DROP,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.drop_catalog",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private OutputDataset createTableDataset(NameIdentifier identifier, TableInfo tableInfo) {
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    List<GravitinoColumnMetadata> columnMetadataList = new ArrayList<>();

    if (tableInfo != null && tableInfo.columns() != null) {
      for (Column column : tableInfo.columns()) {
        fields.add(
            openLineage.newSchemaDatasetFacetFields(
                column.name(), column.dataType().simpleString(), column.comment(), null));

        // 收集列扩展元数据
        columnMetadataList.add(
            GravitinoColumnMetadata.builder()
                .name(column.name())
                .nullable(column.nullable())
                .autoIncrement(column.autoIncrement())
                .defaultValue(formatDefaultValue(column.defaultValue()))
                .build());
      }
    }

    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    // 构建 Gravitino 自定义 facet
    GravitinoDatasetFacet gravitinoFacet = buildGravitinoFacet(tableInfo, columnMetadataList);

    DatasetFacetsBuilder facetsBuilder =
        openLineage
            .newDatasetFacetsBuilder()
            .schema(schemaFacet)
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.CREATE,
                    null));

    // 添加自定义 facet
    if (gravitinoFacet != null) {
      facetsBuilder.put(GRAVITINO_FACET_KEY, gravitinoFacet);
    }

    return openLineage
        .newOutputDatasetBuilder()
        .namespace(formatDatasetNamespace(identifier))
        .name(formatDatasetName(identifier))
        .facets(facetsBuilder.build())
        .build();
  }

  /** 构建 Gravitino 自定义 facet */
  private GravitinoDatasetFacet buildGravitinoFacet(
      TableInfo tableInfo, List<GravitinoColumnMetadata> columnMetadataList) {
    if (tableInfo == null) {
      return null;
    }

    GravitinoDatasetFacet.GravitinoDatasetFacetBuilder builder =
        GravitinoDatasetFacet.builder(producerUri)
            .description(tableInfo.comment())
            .properties(tableInfo.properties())
            .columns(columnMetadataList);

    // 分区信息
    if (tableInfo.partitioning() != null && tableInfo.partitioning().length > 0) {
      builder.partitions(
          Arrays.stream(tableInfo.partitioning())
              .map(Object::toString)
              .collect(Collectors.joining(", ")));
    }

    // 分布信息
    if (tableInfo.distribution() != null) {
      builder.distribution(tableInfo.distribution().toString());
    }

    // 排序信息
    if (tableInfo.sortOrder() != null && tableInfo.sortOrder().length > 0) {
      builder.sortOrders(
          Arrays.stream(tableInfo.sortOrder())
              .map(Object::toString)
              .collect(Collectors.joining(", ")));
    }

    // 索引信息
    if (tableInfo.index() != null && tableInfo.index().length > 0) {
      builder.indexes(
          Arrays.stream(tableInfo.index()).map(Object::toString).collect(Collectors.joining(", ")));
    }

    // 审计信息
    Audit audit = tableInfo.auditInfo();
    if (audit != null) {
      GravitinoDatasetFacet.fromAudit(builder, audit);
    }

    return builder.build();
  }

  /** 格式化默认值表达式 */
  private String formatDefaultValue(Expression defaultValue) {
    if (defaultValue == null || defaultValue == Column.DEFAULT_VALUE_NOT_SET) {
      return null;
    }
    return defaultValue.toString();
  }

  private RunEvent createRunEvent(
      Event event,
      String jobName,
      OpenLineage.RunEvent.EventType eventType,
      List<InputDataset> inputs,
      List<OutputDataset> outputs) {

    UUID runId = UUID.randomUUID();
    ZonedDateTime eventTime =
        ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(event.eventTime()), ZoneOffset.UTC);

    Run run = openLineage.newRunBuilder().runId(runId).build();

    Job job = openLineage.newJobBuilder().namespace(namespace).name(jobName).build();

    return openLineage
        .newRunEventBuilder()
        .eventType(eventType)
        .eventTime(eventTime)
        .run(run)
        .job(job)
        .inputs(inputs)
        .outputs(outputs)
        .build();
  }

  /**
   * Format dataset namespace from Gravitino identifier.
   *
   * <p>Format: gravitino://{metalake}/{catalog}
   */
  private String formatDatasetNamespace(NameIdentifier identifier) {
    String[] parts = identifier.namespace().levels();
    if (parts.length >= 2) {
      return String.format("gravitino://%s/%s", parts[0], parts[1]);
    } else if (parts.length == 1) {
      return String.format("gravitino://%s", parts[0]);
    }
    return namespace;
  }

  /**
   * Format dataset name from Gravitino identifier.
   *
   * <p>Format: {schema}.{table} or just {name}
   */
  private String formatDatasetName(NameIdentifier identifier) {
    String[] parts = identifier.namespace().levels();
    if (parts.length >= 3) {
      // metalake.catalog.schema.table -> schema.table
      return parts[2] + "." + identifier.name();
    }
    return identifier.name();
  }

  // ============================= Metric 事件转换 =============================

  private RunEvent convertRegisterMetric(RegisterMetricEvent event) {
    NameIdentifier identifier = event.identifier();
    MetricInfo metricInfo = event.registeredMetricInfo();

    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("code", "STRING", metricInfo.code(), null));
    fields.add(
        openLineage.newSchemaDatasetFacetFields(
            "type", "STRING", metricInfo.metricType().name(), null));
    metricInfo
        .comment()
        .ifPresent(
            c -> fields.add(openLineage.newSchemaDatasetFacetFields("comment", "STRING", c, null)));
    metricInfo
        .unit()
        .ifPresent(
            u -> fields.add(openLineage.newSchemaDatasetFacetFields("unit", "STRING", u, null)));
    metricInfo
        .aggregationLogic()
        .ifPresent(
            a ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields(
                        "aggregationLogic", "STRING", a, null)));
    metricInfo
        .calculationFormula()
        .ifPresent(
            f ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields(
                        "calculationFormula", "STRING", f, null)));
    // 发送 parentMetricCodes（用逗号分隔的字符串）
    String[] parentCodes = metricInfo.parentMetricCodes();
    if (parentCodes != null && parentCodes.length > 0) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < parentCodes.length; i++) {
        if (i > 0) sb.append(",");
        sb.append(parentCodes[i]);
      }
      fields.add(
          openLineage.newSchemaDatasetFacetFields(
              "parentMetricCodes", "STRING", sb.toString(), null));
    }

    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    DatasetFacets facets =
        openLineage
            .newDatasetFacetsBuilder()
            .schema(schemaFacet)
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.CREATE, null))
            .build();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(facets)
            .build();

    return createRunEvent(
        event,
        "gravitino.register_metric",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDeleteMetric(DeleteMetricEvent event) {
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.DROP,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.delete_metric",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  // ============================= WordRoot 事件转换 =============================

  private RunEvent convertCreateWordRoot(CreateWordRootEvent event) {
    NameIdentifier identifier = event.identifier();
    WordRootInfo wordRootInfo = event.createdWordRootInfo();

    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(
        openLineage.newSchemaDatasetFacetFields("code", "STRING", wordRootInfo.code(), null));
    wordRootInfo
        .nameCn()
        .ifPresent(
            cn ->
                fields.add(openLineage.newSchemaDatasetFacetFields("nameCn", "STRING", cn, null)));
    wordRootInfo
        .nameEn()
        .ifPresent(
            en ->
                fields.add(openLineage.newSchemaDatasetFacetFields("nameEn", "STRING", en, null)));
    wordRootInfo
        .comment()
        .ifPresent(
            c -> fields.add(openLineage.newSchemaDatasetFacetFields("comment", "STRING", c, null)));

    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    DatasetFacets facets =
        openLineage
            .newDatasetFacetsBuilder()
            .schema(schemaFacet)
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.CREATE, null))
            .build();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(facets)
            .build();

    return createRunEvent(
        event,
        "gravitino.create_wordroot",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDeleteWordRoot(DeleteWordRootEvent event) {
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.DROP,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.delete_wordroot",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }
}
