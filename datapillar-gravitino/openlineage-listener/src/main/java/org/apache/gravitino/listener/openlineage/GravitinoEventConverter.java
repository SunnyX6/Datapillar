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
import org.apache.gravitino.MetadataObject;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.listener.api.event.AlterCatalogEvent;
import org.apache.gravitino.listener.api.event.AlterMetricEvent;
import org.apache.gravitino.listener.api.event.AlterModifierEvent;
import org.apache.gravitino.listener.api.event.AlterSchemaEvent;
import org.apache.gravitino.listener.api.event.AlterTableEvent;
import org.apache.gravitino.listener.api.event.AlterUnitEvent;
import org.apache.gravitino.listener.api.event.AlterValueDomainEvent;
import org.apache.gravitino.listener.api.event.AlterWordRootEvent;
import org.apache.gravitino.listener.api.event.AssociateTagsForMetadataObjectEvent;
import org.apache.gravitino.listener.api.event.CreateCatalogEvent;
import org.apache.gravitino.listener.api.event.CreateModifierEvent;
import org.apache.gravitino.listener.api.event.CreateSchemaEvent;
import org.apache.gravitino.listener.api.event.CreateTableEvent;
import org.apache.gravitino.listener.api.event.CreateUnitEvent;
import org.apache.gravitino.listener.api.event.CreateValueDomainEvent;
import org.apache.gravitino.listener.api.event.CreateWordRootEvent;
import org.apache.gravitino.listener.api.event.DropCatalogEvent;
import org.apache.gravitino.listener.api.event.DropMetricEvent;
import org.apache.gravitino.listener.api.event.DropModifierEvent;
import org.apache.gravitino.listener.api.event.DropSchemaEvent;
import org.apache.gravitino.listener.api.event.DropTableEvent;
import org.apache.gravitino.listener.api.event.DropUnitEvent;
import org.apache.gravitino.listener.api.event.DropValueDomainEvent;
import org.apache.gravitino.listener.api.event.DropWordRootEvent;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.api.event.LoadSchemaEvent;
import org.apache.gravitino.listener.api.event.LoadTableEvent;
import org.apache.gravitino.listener.api.event.RegisterMetricEvent;
import org.apache.gravitino.listener.api.info.CatalogInfo;
import org.apache.gravitino.listener.api.info.MetricInfo;
import org.apache.gravitino.listener.api.info.ModifierInfo;
import org.apache.gravitino.listener.api.info.SchemaInfo;
import org.apache.gravitino.listener.api.info.TableInfo;
import org.apache.gravitino.listener.api.info.UnitInfo;
import org.apache.gravitino.listener.api.info.ValueDomainInfo;
import org.apache.gravitino.listener.api.info.WordRootInfo;
import org.apache.gravitino.listener.openlineage.facets.GravitinoDatasetFacet;
import org.apache.gravitino.listener.openlineage.facets.GravitinoDatasetFacet.GravitinoColumnMetadata;
import org.apache.gravitino.listener.openlineage.facets.GravitinoTagFacet;
import org.apache.gravitino.listener.openlineage.facets.TableChangeInfo;
import org.apache.gravitino.rel.Column;
import org.apache.gravitino.rel.TableChange;
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
    } else if (event instanceof AlterSchemaEvent) {
      return convertAlterSchema((AlterSchemaEvent) event);
    } else if (event instanceof DropSchemaEvent) {
      return convertDropSchema((DropSchemaEvent) event);
    } else if (event instanceof LoadSchemaEvent) {
      return convertLoadSchema((LoadSchemaEvent) event);
    } else if (event instanceof CreateCatalogEvent) {
      return convertCreateCatalog((CreateCatalogEvent) event);
    } else if (event instanceof AlterCatalogEvent) {
      return convertAlterCatalog((AlterCatalogEvent) event);
    } else if (event instanceof DropCatalogEvent) {
      return convertDropCatalog((DropCatalogEvent) event);
    } else if (event instanceof RegisterMetricEvent) {
      return convertRegisterMetric((RegisterMetricEvent) event);
    } else if (event instanceof AlterMetricEvent) {
      return convertAlterMetric((AlterMetricEvent) event);
    } else if (event instanceof DropMetricEvent) {
      return convertDropMetric((DropMetricEvent) event);
    } else if (event instanceof CreateWordRootEvent) {
      return convertCreateWordRoot((CreateWordRootEvent) event);
    } else if (event instanceof AlterWordRootEvent) {
      return convertAlterWordRoot((AlterWordRootEvent) event);
    } else if (event instanceof DropWordRootEvent) {
      return convertDropWordRoot((DropWordRootEvent) event);
    } else if (event instanceof CreateModifierEvent) {
      return convertCreateModifier((CreateModifierEvent) event);
    } else if (event instanceof AlterModifierEvent) {
      return convertAlterModifier((AlterModifierEvent) event);
    } else if (event instanceof DropModifierEvent) {
      return convertDropModifier((DropModifierEvent) event);
    } else if (event instanceof CreateUnitEvent) {
      return convertCreateUnit((CreateUnitEvent) event);
    } else if (event instanceof AlterUnitEvent) {
      return convertAlterUnit((AlterUnitEvent) event);
    } else if (event instanceof DropUnitEvent) {
      return convertDropUnit((DropUnitEvent) event);
    } else if (event instanceof CreateValueDomainEvent) {
      return convertCreateValueDomain((CreateValueDomainEvent) event);
    } else if (event instanceof AlterValueDomainEvent) {
      return convertAlterValueDomain((AlterValueDomainEvent) event);
    } else if (event instanceof DropValueDomainEvent) {
      return convertDropValueDomain((DropValueDomainEvent) event);
    } else if (event instanceof AssociateTagsForMetadataObjectEvent) {
      return convertAssociateTagsForMetadataObject((AssociateTagsForMetadataObjectEvent) event);
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
    TableChange[] tableChanges = event.tableChanges();

    // 解析 tableChanges 构建 changes 列表
    List<TableChangeInfo> changes = parseTableChanges(tableChanges);

    OutputDataset outputDataset = createAlterTableDataset(identifier, tableInfo, changes);

    return createRunEvent(
        event,
        "gravitino.alter_table",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  /** 解析 TableChange 数组，构建 TableChangeInfo 列表 */
  private List<TableChangeInfo> parseTableChanges(TableChange[] tableChanges) {
    if (tableChanges == null || tableChanges.length == 0) {
      return new ArrayList<>();
    }

    return Arrays.stream(tableChanges)
        .map(this::convertTableChange)
        .filter(info -> info != null)
        .collect(Collectors.toList());
  }

  /** 将单个 TableChange 转换为 TableChangeInfo */
  private TableChangeInfo convertTableChange(TableChange change) {
    if (change instanceof TableChange.RenameTable) {
      TableChange.RenameTable rename = (TableChange.RenameTable) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.RENAME_TABLE)
          .newName(rename.getNewName())
          .build();
    } else if (change instanceof TableChange.UpdateComment) {
      TableChange.UpdateComment update = (TableChange.UpdateComment) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.UPDATE_COMMENT)
          .newComment(update.getNewComment())
          .build();
    } else if (change instanceof TableChange.SetProperty) {
      TableChange.SetProperty set = (TableChange.SetProperty) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.SET_PROPERTY)
          .propertyKey(set.getProperty())
          .propertyValue(set.getValue())
          .build();
    } else if (change instanceof TableChange.RemoveProperty) {
      TableChange.RemoveProperty remove = (TableChange.RemoveProperty) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.REMOVE_PROPERTY)
          .propertyKey(remove.getProperty())
          .build();
    } else if (change instanceof TableChange.AddColumn) {
      TableChange.AddColumn add = (TableChange.AddColumn) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.ADD_COLUMN)
          .columnName(String.join(".", add.fieldName()))
          .dataType(add.getDataType().simpleString())
          .columnComment(add.getComment())
          .nullable(add.isNullable())
          .autoIncrement(add.isAutoIncrement())
          .defaultValue(formatDefaultValue(add.getDefaultValue()))
          .position(formatColumnPosition(add.getPosition()))
          .build();
    } else if (change instanceof TableChange.DeleteColumn) {
      TableChange.DeleteColumn delete = (TableChange.DeleteColumn) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.DELETE_COLUMN)
          .columnName(String.join(".", delete.fieldName()))
          .build();
    } else if (change instanceof TableChange.RenameColumn) {
      TableChange.RenameColumn rename = (TableChange.RenameColumn) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.RENAME_COLUMN)
          .oldColumnName(String.join(".", rename.fieldName()))
          .newColumnName(rename.getNewName())
          .build();
    } else if (change instanceof TableChange.UpdateColumnType) {
      TableChange.UpdateColumnType update = (TableChange.UpdateColumnType) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.UPDATE_COLUMN_TYPE)
          .columnName(String.join(".", update.fieldName()))
          .dataType(update.getNewDataType().simpleString())
          .build();
    } else if (change instanceof TableChange.UpdateColumnComment) {
      TableChange.UpdateColumnComment update = (TableChange.UpdateColumnComment) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.UPDATE_COLUMN_COMMENT)
          .columnName(String.join(".", update.fieldName()))
          .newComment(update.getNewComment())
          .build();
    } else if (change instanceof TableChange.UpdateColumnPosition) {
      TableChange.UpdateColumnPosition update = (TableChange.UpdateColumnPosition) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.UPDATE_COLUMN_POSITION)
          .columnName(String.join(".", update.fieldName()))
          .position(formatColumnPosition(update.getPosition()))
          .build();
    } else if (change instanceof TableChange.UpdateColumnNullability) {
      TableChange.UpdateColumnNullability update = (TableChange.UpdateColumnNullability) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.UPDATE_COLUMN_NULLABILITY)
          .columnName(String.join(".", update.fieldName()))
          .nullable(update.nullable())
          .build();
    } else if (change instanceof TableChange.UpdateColumnDefaultValue) {
      TableChange.UpdateColumnDefaultValue update = (TableChange.UpdateColumnDefaultValue) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.UPDATE_COLUMN_DEFAULT_VALUE)
          .columnName(String.join(".", update.fieldName()))
          .defaultValue(formatDefaultValue(update.getNewDefaultValue()))
          .build();
    } else if (change instanceof TableChange.UpdateColumnAutoIncrement) {
      TableChange.UpdateColumnAutoIncrement update = (TableChange.UpdateColumnAutoIncrement) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.UPDATE_COLUMN_AUTO_INCREMENT)
          .columnName(String.join(".", update.fieldName()))
          .autoIncrement(update.isAutoIncrement())
          .build();
    } else if (change instanceof TableChange.AddIndex) {
      TableChange.AddIndex add = (TableChange.AddIndex) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.ADD_INDEX)
          .indexName(add.getName())
          .indexType(add.getType().name())
          .indexColumns(
              Arrays.stream(add.getFieldNames())
                  .map(arr -> String.join(".", arr))
                  .toArray(String[]::new))
          .build();
    } else if (change instanceof TableChange.DeleteIndex) {
      TableChange.DeleteIndex delete = (TableChange.DeleteIndex) change;
      return TableChangeInfo.builder()
          .type(TableChangeInfo.ChangeType.DELETE_INDEX)
          .indexName(delete.getName())
          .build();
    }

    log.debug("Unsupported TableChange type: {}", change.getClass().getSimpleName());
    return null;
  }

  /** 格式化列位置 */
  private String formatColumnPosition(TableChange.ColumnPosition position) {
    if (position == null) {
      return null;
    }
    if (position instanceof TableChange.First) {
      return "FIRST";
    } else if (position instanceof TableChange.After) {
      return "AFTER " + ((TableChange.After) position).getColumn();
    }
    return null;
  }

  /** 创建 alter_table 事件的 OutputDataset（包含 changes） */
  private OutputDataset createAlterTableDataset(
      NameIdentifier identifier, TableInfo tableInfo, List<TableChangeInfo> changes) {
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    List<GravitinoColumnMetadata> columnMetadataList = new ArrayList<>();

    if (tableInfo != null && tableInfo.columns() != null) {
      for (Column column : tableInfo.columns()) {
        fields.add(
            openLineage.newSchemaDatasetFacetFields(
                column.name(), column.dataType().simpleString(), column.comment(), null));

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

    // 构建包含 changes 的 Gravitino facet
    GravitinoDatasetFacet gravitinoFacet =
        buildAlterTableFacet(tableInfo, columnMetadataList, changes);

    DatasetFacetsBuilder facetsBuilder =
        openLineage
            .newDatasetFacetsBuilder()
            .schema(schemaFacet)
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.ALTER, null));

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

  /** 构建 alter_table 事件的 Gravitino facet（包含 changes） */
  private GravitinoDatasetFacet buildAlterTableFacet(
      TableInfo tableInfo,
      List<GravitinoColumnMetadata> columnMetadataList,
      List<TableChangeInfo> changes) {
    if (tableInfo == null) {
      return GravitinoDatasetFacet.builder(producerUri).changes(changes).build();
    }

    GravitinoDatasetFacet.GravitinoDatasetFacetBuilder builder =
        GravitinoDatasetFacet.builder(producerUri)
            .description(tableInfo.comment())
            .properties(tableInfo.properties())
            .columns(columnMetadataList)
            .changes(changes);

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

  private RunEvent convertAlterSchema(AlterSchemaEvent event) {
    NameIdentifier identifier = event.identifier();
    SchemaInfo schemaInfo = event.updatedSchemaInfo();

    // 构建 Gravitino 自定义 facet
    GravitinoDatasetFacet gravitinoFacet = buildSchemaGravitinoFacet(schemaInfo);

    DatasetFacetsBuilder facetsBuilder =
        openLineage
            .newDatasetFacetsBuilder()
            .documentation(
                openLineage.newDocumentationDatasetFacet(
                    schemaInfo != null ? schemaInfo.comment() : null))
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.ALTER, null));

    if (gravitinoFacet != null) {
      facetsBuilder.put(GRAVITINO_FACET_KEY, gravitinoFacet);
    }

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(identifier.name())
            .facets(facetsBuilder.build())
            .build();

    return createRunEvent(
        event,
        "gravitino.alter_schema",
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

  private RunEvent convertAlterCatalog(AlterCatalogEvent event) {
    NameIdentifier identifier = event.identifier();
    CatalogInfo catalogInfo = event.updatedCatalogInfo();

    // 构建 Gravitino 自定义 facet
    GravitinoDatasetFacet.GravitinoDatasetFacetBuilder facetBuilder =
        GravitinoDatasetFacet.builder(producerUri)
            .description(catalogInfo.comment())
            .properties(catalogInfo.properties());

    // 审计信息
    Audit audit = catalogInfo.auditInfo();
    if (audit != null) {
      GravitinoDatasetFacet.fromAudit(facetBuilder, audit);
    }

    DatasetFacetsBuilder facetsBuilder =
        openLineage
            .newDatasetFacetsBuilder()
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.ALTER, null));
    facetsBuilder.put(GRAVITINO_FACET_KEY, facetBuilder.build());

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(namespace)
            .name(identifier.name())
            .facets(facetsBuilder.build())
            .build();

    return createRunEvent(
        event,
        "gravitino.alter_catalog",
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

    // 发送原子指标的 ref 字段
    metricInfo
        .refCatalogName()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("refCatalogName", "STRING", v, null)));
    metricInfo
        .refSchemaName()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("refSchemaName", "STRING", v, null)));
    metricInfo
        .refTableName()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("refTableName", "STRING", v, null)));
    metricInfo
        .measureColumns()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("measureColumns", "JSON", v, null)));
    metricInfo
        .filterColumns()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("filterColumns", "JSON", v, null)));

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

  private RunEvent convertAlterMetric(AlterMetricEvent event) {
    NameIdentifier identifier = event.identifier();
    MetricInfo metricInfo = event.updatedMetricInfo();

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
        .calculationFormula()
        .ifPresent(
            f ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields(
                        "calculationFormula", "STRING", f, null)));
    // 发送 parentMetricCodes
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

    // 发送原子指标的 ref 字段
    metricInfo
        .refCatalogName()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("refCatalogName", "STRING", v, null)));
    metricInfo
        .refSchemaName()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("refSchemaName", "STRING", v, null)));
    metricInfo
        .refTableName()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("refTableName", "STRING", v, null)));
    metricInfo
        .measureColumns()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("measureColumns", "JSON", v, null)));
    metricInfo
        .filterColumns()
        .ifPresent(
            v ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("filterColumns", "JSON", v, null)));

    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    DatasetFacets facets =
        openLineage
            .newDatasetFacetsBuilder()
            .schema(schemaFacet)
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.ALTER, null))
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
        "gravitino.alter_metric",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropMetric(DropMetricEvent event) {
    NameIdentifier identifier = event.identifier();

    // 添加 schema facet 传递 code，用于 Sink 端正确生成 ID
    String code = identifier.name();
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("code", "STRING", code, null));
    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .schema(schemaFacet)
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.DROP,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.drop_metric",
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
        .name()
        .ifPresent(
            name ->
                fields.add(openLineage.newSchemaDatasetFacetFields("name", "STRING", name, null)));
    wordRootInfo
        .dataType()
        .ifPresent(
            dt ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("dataType", "STRING", dt, null)));
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

  private RunEvent convertAlterWordRoot(AlterWordRootEvent event) {
    NameIdentifier identifier = event.identifier();
    WordRootInfo wordRootInfo = event.updatedWordRootInfo();

    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(
        openLineage.newSchemaDatasetFacetFields("code", "STRING", wordRootInfo.code(), null));
    wordRootInfo
        .name()
        .ifPresent(
            name ->
                fields.add(openLineage.newSchemaDatasetFacetFields("name", "STRING", name, null)));
    wordRootInfo
        .dataType()
        .ifPresent(
            dt ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("dataType", "STRING", dt, null)));
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
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.ALTER, null))
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
        "gravitino.alter_wordroot",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropWordRoot(DropWordRootEvent event) {
    NameIdentifier identifier = event.identifier();

    // 添加 schema facet 传递 code，用于 Sink 端正确生成 ID
    String code = identifier.name();
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("code", "STRING", code, null));
    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .schema(schemaFacet)
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.DROP,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.drop_wordroot",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  // ============================= Tag 事件转换 =============================

  /**
   * 转换 AssociateTagsForMetadataObjectEvent（关联 Tag 到元数据对象）。
   *
   * <p>将 Tag 关联信息作为自定义 facet 传递给 OpenLineage。
   */
  private RunEvent convertAssociateTagsForMetadataObject(
      AssociateTagsForMetadataObjectEvent event) {
    NameIdentifier identifier = event.identifier();
    MetadataObject.Type objectType = event.objectType();

    // 根据对象类型构建 namespace 和 name
    String datasetNamespace = formatTagDatasetNamespace(identifier, objectType);
    String datasetName = formatTagDatasetName(identifier, objectType);

    // 构建包含 Tag 信息的 facet
    GravitinoTagFacet tagFacet =
        GravitinoTagFacet.builder(producerUri)
            .objectType(objectType.name())
            .tagsToAdd(event.tagsToAdd())
            .tagsToRemove(event.tagsToRemove())
            .associatedTags(event.associatedTags())
            .build();

    DatasetFacetsBuilder facetsBuilder = openLineage.newDatasetFacetsBuilder();
    facetsBuilder.put("gravitinoTag", tagFacet);

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(datasetNamespace)
            .name(datasetName)
            .facets(facetsBuilder.build())
            .build();

    return createRunEvent(
        event,
        "gravitino.associate_tags",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  /**
   * 根据对象类型格式化 Tag 事件的 dataset namespace。
   *
   * <p>格式：
   *
   * <ul>
   *   <li>CATALOG: gravitino://{metalake}
   *   <li>SCHEMA: gravitino://{metalake}/{catalog}
   *   <li>TABLE/COLUMN: gravitino://{metalake}/{catalog}
   * </ul>
   */
  private String formatTagDatasetNamespace(NameIdentifier identifier, MetadataObject.Type type) {
    String[] parts = identifier.namespace().levels();

    switch (type) {
      case CATALOG:
        // identifier: metalake.catalog, namespace.levels = [metalake]
        if (parts.length >= 1) {
          return String.format("gravitino://%s", parts[0]);
        }
        break;
      case SCHEMA:
        // identifier: metalake.catalog.schema, namespace.levels = [metalake, catalog]
        if (parts.length >= 2) {
          return String.format("gravitino://%s/%s", parts[0], parts[1]);
        }
        break;
      case TABLE:
      case COLUMN:
        // identifier: metalake.catalog.schema.table, namespace.levels = [metalake, catalog, schema]
        if (parts.length >= 2) {
          return String.format("gravitino://%s/%s", parts[0], parts[1]);
        }
        break;
      default:
        break;
    }
    return namespace;
  }

  /**
   * 根据对象类型格式化 Tag 事件的 dataset name。
   *
   * <p>格式：
   *
   * <ul>
   *   <li>CATALOG: {catalog}
   *   <li>SCHEMA: {schema}
   *   <li>TABLE: {schema}.{table}
   *   <li>COLUMN: {schema}.{table}.{column}
   * </ul>
   */
  private String formatTagDatasetName(NameIdentifier identifier, MetadataObject.Type type) {
    String[] parts = identifier.namespace().levels();
    String name = identifier.name();

    switch (type) {
      case CATALOG:
        // identifier.name = catalog
        return name;
      case SCHEMA:
        // identifier.name = schema
        return name;
      case TABLE:
        // identifier.name = table, parts[2] = schema
        if (parts.length >= 3) {
          return parts[2] + "." + name;
        }
        return name;
      case COLUMN:
        // identifier.name = column, parts[2] = schema, parts[3] = table
        if (parts.length >= 4) {
          return parts[2] + "." + parts[3] + "." + name;
        }
        return name;
      default:
        return name;
    }
  }

  // ============================= Modifier 事件转换 =============================

  private RunEvent convertCreateModifier(CreateModifierEvent event) {
    NameIdentifier identifier = event.identifier();
    ModifierInfo modifierInfo = event.createdModifierInfo();

    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(
        openLineage.newSchemaDatasetFacetFields("code", "STRING", modifierInfo.code(), null));
    modifierInfo
        .modifierType()
        .ifPresent(
            t -> fields.add(openLineage.newSchemaDatasetFacetFields("type", "STRING", t, null)));
    modifierInfo
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
        "gravitino.create_modifier",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertAlterModifier(AlterModifierEvent event) {
    NameIdentifier identifier = event.identifier();
    ModifierInfo modifierInfo = event.updatedModifierInfo();

    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(
        openLineage.newSchemaDatasetFacetFields("code", "STRING", modifierInfo.code(), null));
    modifierInfo
        .modifierType()
        .ifPresent(
            t -> fields.add(openLineage.newSchemaDatasetFacetFields("type", "STRING", t, null)));
    modifierInfo
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
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.ALTER, null))
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
        "gravitino.alter_modifier",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropModifier(DropModifierEvent event) {
    NameIdentifier identifier = event.identifier();

    // 添加 schema facet 传递 code，用于 Sink 端正确生成 ID
    String code = identifier.name();
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("code", "STRING", code, null));
    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .schema(schemaFacet)
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.DROP,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.drop_modifier",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  // ============================= Unit 事件转换 =============================

  private RunEvent convertCreateUnit(CreateUnitEvent event) {
    NameIdentifier identifier = event.identifier();
    UnitInfo unitInfo = event.createdUnitInfo();

    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("code", "STRING", unitInfo.code(), null));
    unitInfo
        .name()
        .ifPresent(
            n -> fields.add(openLineage.newSchemaDatasetFacetFields("name", "STRING", n, null)));
    unitInfo
        .symbol()
        .ifPresent(
            s -> fields.add(openLineage.newSchemaDatasetFacetFields("symbol", "STRING", s, null)));
    unitInfo
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
        "gravitino.create_unit",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertAlterUnit(AlterUnitEvent event) {
    NameIdentifier identifier = event.identifier();
    UnitInfo unitInfo = event.updatedUnitInfo();

    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("code", "STRING", unitInfo.code(), null));
    unitInfo
        .name()
        .ifPresent(
            n -> fields.add(openLineage.newSchemaDatasetFacetFields("name", "STRING", n, null)));
    unitInfo
        .symbol()
        .ifPresent(
            s -> fields.add(openLineage.newSchemaDatasetFacetFields("symbol", "STRING", s, null)));
    unitInfo
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
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.ALTER, null))
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
        "gravitino.alter_unit",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropUnit(DropUnitEvent event) {
    NameIdentifier identifier = event.identifier();

    // 添加 schema facet 传递 code，用于 Sink 端正确生成 ID
    String code = identifier.name();
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(openLineage.newSchemaDatasetFacetFields("code", "STRING", code, null));
    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .schema(schemaFacet)
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.DROP,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.drop_unit",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  // ============================= ValueDomain 事件转换 =============================

  private RunEvent convertCreateValueDomain(CreateValueDomainEvent event) {
    NameIdentifier identifier = event.identifier();
    ValueDomainInfo valueDomainInfo = event.createdValueDomainInfo();

    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(
        openLineage.newSchemaDatasetFacetFields(
            "domainCode", "STRING", valueDomainInfo.domainCode(), null));
    valueDomainInfo
        .domainName()
        .ifPresent(
            n ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("domainName", "STRING", n, null)));
    fields.add(
        openLineage.newSchemaDatasetFacetFields(
            "domainType", "STRING", valueDomainInfo.domainType().name(), null));
    fields.add(
        openLineage.newSchemaDatasetFacetFields(
            "domainLevel", "STRING", valueDomainInfo.domainLevel().name(), null));
    if (valueDomainInfo.items() != null && !valueDomainInfo.items().isEmpty()) {
      String itemsStr =
          valueDomainInfo.items().stream()
              .map(item -> item.value() + ":" + (item.label() != null ? item.label() : ""))
              .collect(Collectors.joining(","));
      fields.add(openLineage.newSchemaDatasetFacetFields("items", "STRING", itemsStr, null));
    }
    valueDomainInfo
        .comment()
        .ifPresent(
            c -> fields.add(openLineage.newSchemaDatasetFacetFields("comment", "STRING", c, null)));
    valueDomainInfo
        .dataType()
        .ifPresent(
            dt ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("dataType", "STRING", dt, null)));

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
        "gravitino.create_valuedomain",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertAlterValueDomain(AlterValueDomainEvent event) {
    NameIdentifier identifier = event.identifier();
    ValueDomainInfo valueDomainInfo = event.updatedValueDomainInfo();

    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(
        openLineage.newSchemaDatasetFacetFields(
            "domainCode", "STRING", valueDomainInfo.domainCode(), null));
    valueDomainInfo
        .domainName()
        .ifPresent(
            n ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("domainName", "STRING", n, null)));
    fields.add(
        openLineage.newSchemaDatasetFacetFields(
            "domainType", "STRING", valueDomainInfo.domainType().name(), null));
    fields.add(
        openLineage.newSchemaDatasetFacetFields(
            "domainLevel", "STRING", valueDomainInfo.domainLevel().name(), null));
    if (valueDomainInfo.items() != null && !valueDomainInfo.items().isEmpty()) {
      String itemsStr =
          valueDomainInfo.items().stream()
              .map(item -> item.value() + ":" + (item.label() != null ? item.label() : ""))
              .collect(Collectors.joining(","));
      fields.add(openLineage.newSchemaDatasetFacetFields("items", "STRING", itemsStr, null));
    }
    valueDomainInfo
        .comment()
        .ifPresent(
            c -> fields.add(openLineage.newSchemaDatasetFacetFields("comment", "STRING", c, null)));
    valueDomainInfo
        .dataType()
        .ifPresent(
            dt ->
                fields.add(
                    openLineage.newSchemaDatasetFacetFields("dataType", "STRING", dt, null)));

    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    DatasetFacets facets =
        openLineage
            .newDatasetFacetsBuilder()
            .schema(schemaFacet)
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.ALTER, null))
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
        "gravitino.alter_valuedomain",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropValueDomain(DropValueDomainEvent event) {
    NameIdentifier identifier = event.identifier();

    // identifier.name() 就是 domainCode
    List<SchemaDatasetFacetFields> fields = new ArrayList<>();
    fields.add(
        openLineage.newSchemaDatasetFacetFields("domainCode", "STRING", identifier.name(), null));

    SchemaDatasetFacet schemaFacet = openLineage.newSchemaDatasetFacet(fields);

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(identifier))
            .name(formatDatasetName(identifier))
            .facets(
                openLineage
                    .newDatasetFacetsBuilder()
                    .schema(schemaFacet)
                    .lifecycleStateChange(
                        openLineage.newLifecycleStateChangeDatasetFacet(
                            OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.DROP,
                            null))
                    .build())
            .build();

    return createRunEvent(
        event,
        "gravitino.drop_valuedomain",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }
}
