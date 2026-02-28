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

package org.apache.gravitino.listener.openlineage.converters;

import io.openlineage.client.OpenLineage;
import io.openlineage.client.OpenLineage.DatasetFacetsBuilder;
import io.openlineage.client.OpenLineage.InputDataset;
import io.openlineage.client.OpenLineage.OutputDataset;
import io.openlineage.client.OpenLineage.RunEvent;
import io.openlineage.client.OpenLineage.SchemaDatasetFacet;
import io.openlineage.client.OpenLineage.SchemaDatasetFacetFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.gravitino.Audit;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.listener.api.event.AlterTableEvent;
import org.apache.gravitino.listener.api.event.CreateTableEvent;
import org.apache.gravitino.listener.api.event.DropTableEvent;
import org.apache.gravitino.listener.api.event.Event;
import org.apache.gravitino.listener.api.event.LoadTableEvent;
import org.apache.gravitino.listener.api.info.TableInfo;
import org.apache.gravitino.listener.openlineage.facets.GravitinoDatasetFacet;
import org.apache.gravitino.listener.openlineage.facets.GravitinoDatasetFacet.GravitinoColumnMetadata;
import org.apache.gravitino.listener.openlineage.facets.TableChangeInfo;
import org.apache.gravitino.rel.Column;
import org.apache.gravitino.rel.TableChange;
import org.apache.gravitino.rel.expressions.Expression;

/**
 * Table 事件转换器。
 *
 * <p>处理: CreateTableEvent, AlterTableEvent, DropTableEvent, LoadTableEvent
 */
@Slf4j
public class TableEventConverter extends BaseEventConverter {

  private static final String GRAVITINO_FACET_KEY = "gravitino";

  public TableEventConverter(OpenLineage openLineage, String namespace) {
    super(openLineage, namespace);
  }

  public RunEvent convert(Event event) {
    if (event instanceof CreateTableEvent) {
      return convertCreateTable((CreateTableEvent) event);
    } else if (event instanceof AlterTableEvent) {
      return convertAlterTable((AlterTableEvent) event);
    } else if (event instanceof DropTableEvent) {
      return convertDropTable((DropTableEvent) event);
    } else if (event instanceof LoadTableEvent) {
      return convertLoadTable((LoadTableEvent) event);
    }
    return null;
  }

  private RunEvent convertCreateTable(CreateTableEvent event) {
    TableInfo tableInfo = event.createdTableInfo();
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset = createTableDataset(event, identifier, tableInfo);

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

    List<TableChangeInfo> changes = parseTableChanges(tableChanges);
    OutputDataset outputDataset = createAlterTableDataset(event, identifier, tableInfo, changes);

    return createRunEvent(
        event,
        "gravitino.alter_table",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.emptyList(),
        Collections.singletonList(outputDataset));
  }

  private RunEvent convertDropTable(DropTableEvent event) {
    NameIdentifier identifier = event.identifier();

    OutputDataset outputDataset =
        openLineage
            .newOutputDatasetBuilder()
            .namespace(formatDatasetNamespace(event, identifier))
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

  private RunEvent convertLoadTable(LoadTableEvent event) {
    TableInfo tableInfo = event.loadedTableInfo();
    NameIdentifier identifier = event.identifier();

    InputDataset inputDataset = createTableInputDataset(event, identifier, tableInfo);

    return createRunEvent(
        event,
        "gravitino.load_table",
        OpenLineage.RunEvent.EventType.COMPLETE,
        Collections.singletonList(inputDataset),
        Collections.emptyList());
  }

  private OutputDataset createTableDataset(
      Event event, NameIdentifier identifier, TableInfo tableInfo) {
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
    GravitinoDatasetFacet gravitinoFacet =
        buildGravitinoFacet(event, tableInfo, columnMetadataList);

    DatasetFacetsBuilder facetsBuilder =
        openLineage
            .newDatasetFacetsBuilder()
            .schema(schemaFacet)
            .lifecycleStateChange(
                openLineage.newLifecycleStateChangeDatasetFacet(
                    OpenLineage.LifecycleStateChangeDatasetFacet.LifecycleStateChange.CREATE,
                    null));

    if (gravitinoFacet != null) {
      facetsBuilder.put(GRAVITINO_FACET_KEY, gravitinoFacet);
    }

    return openLineage
        .newOutputDatasetBuilder()
        .namespace(formatDatasetNamespace(event, identifier))
        .name(formatDatasetName(identifier))
        .facets(facetsBuilder.build())
        .build();
  }

  private InputDataset createTableInputDataset(
      Event event, NameIdentifier identifier, TableInfo tableInfo) {
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
    GravitinoDatasetFacet gravitinoFacet =
        buildGravitinoFacet(event, tableInfo, columnMetadataList);

    DatasetFacetsBuilder facetsBuilder = openLineage.newDatasetFacetsBuilder().schema(schemaFacet);

    if (gravitinoFacet != null) {
      facetsBuilder.put(GRAVITINO_FACET_KEY, gravitinoFacet);
    }

    return openLineage
        .newInputDatasetBuilder()
        .namespace(formatDatasetNamespace(event, identifier))
        .name(formatDatasetName(identifier))
        .facets(facetsBuilder.build())
        .build();
  }

  private OutputDataset createAlterTableDataset(
      Event event, NameIdentifier identifier, TableInfo tableInfo, List<TableChangeInfo> changes) {
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
    GravitinoDatasetFacet gravitinoFacet =
        buildAlterTableFacet(event, tableInfo, columnMetadataList, changes);

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
        .namespace(formatDatasetNamespace(event, identifier))
        .name(formatDatasetName(identifier))
        .facets(facetsBuilder.build())
        .build();
  }

  private GravitinoDatasetFacet buildGravitinoFacet(
      Event event, TableInfo tableInfo, List<GravitinoColumnMetadata> columnMetadataList) {
    if (tableInfo == null) {
      return null;
    }

    GravitinoDatasetFacet.GravitinoDatasetFacetBuilder builder =
        tenantFacetBuilder(event)
            .description(tableInfo.comment())
            .properties(tableInfo.properties())
            .columns(columnMetadataList);

    if (tableInfo.partitioning() != null && tableInfo.partitioning().length > 0) {
      builder.partitions(
          Arrays.stream(tableInfo.partitioning())
              .map(Object::toString)
              .collect(Collectors.joining(", ")));
    }

    if (tableInfo.distribution() != null) {
      builder.distribution(tableInfo.distribution().toString());
    }

    if (tableInfo.sortOrder() != null && tableInfo.sortOrder().length > 0) {
      builder.sortOrders(
          Arrays.stream(tableInfo.sortOrder())
              .map(Object::toString)
              .collect(Collectors.joining(", ")));
    }

    if (tableInfo.index() != null && tableInfo.index().length > 0) {
      builder.indexes(
          Arrays.stream(tableInfo.index()).map(Object::toString).collect(Collectors.joining(", ")));
    }

    Audit audit = tableInfo.auditInfo();
    if (audit != null) {
      GravitinoDatasetFacet.fromAudit(builder, audit);
    }

    return builder.build();
  }

  private GravitinoDatasetFacet buildAlterTableFacet(
      Event event,
      TableInfo tableInfo,
      List<GravitinoColumnMetadata> columnMetadataList,
      List<TableChangeInfo> changes) {
    if (tableInfo == null) {
      return tenantFacetBuilder(event).changes(changes).build();
    }

    GravitinoDatasetFacet.GravitinoDatasetFacetBuilder builder =
        tenantFacetBuilder(event)
            .description(tableInfo.comment())
            .properties(tableInfo.properties())
            .columns(columnMetadataList)
            .changes(changes);

    if (tableInfo.partitioning() != null && tableInfo.partitioning().length > 0) {
      builder.partitions(
          Arrays.stream(tableInfo.partitioning())
              .map(Object::toString)
              .collect(Collectors.joining(", ")));
    }

    if (tableInfo.distribution() != null) {
      builder.distribution(tableInfo.distribution().toString());
    }

    if (tableInfo.sortOrder() != null && tableInfo.sortOrder().length > 0) {
      builder.sortOrders(
          Arrays.stream(tableInfo.sortOrder())
              .map(Object::toString)
              .collect(Collectors.joining(", ")));
    }

    if (tableInfo.index() != null && tableInfo.index().length > 0) {
      builder.indexes(
          Arrays.stream(tableInfo.index()).map(Object::toString).collect(Collectors.joining(", ")));
    }

    Audit audit = tableInfo.auditInfo();
    if (audit != null) {
      GravitinoDatasetFacet.fromAudit(builder, audit);
    }

    return builder.build();
  }

  private List<TableChangeInfo> parseTableChanges(TableChange[] tableChanges) {
    if (tableChanges == null || tableChanges.length == 0) {
      return new ArrayList<>();
    }

    return Arrays.stream(tableChanges)
        .map(this::convertTableChange)
        .filter(info -> info != null)
        .collect(Collectors.toList());
  }

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

  private String formatDefaultValue(Expression defaultValue) {
    if (defaultValue == null || defaultValue == Column.DEFAULT_VALUE_NOT_SET) {
      return null;
    }
    return defaultValue.toString();
  }
}
