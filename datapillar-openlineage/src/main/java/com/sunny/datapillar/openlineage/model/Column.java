package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Gravitino table column row. */
@Data
public class Column {
  private String namespace;
  private String schemaName;
  private String tableName;
  private Long columnId;
  private Long tableId;
  private Long schemaId;
  private String columnName;
  private String columnType;
  private String columnComment;
}
