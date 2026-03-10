package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Gravitino table row. */
@Data
public class Table {
  private String namespace;
  private String schemaName;
  private Long tableId;
  private Long metalakeId;
  private Long catalogId;
  private Long schemaId;
  private String tableName;
  private String tableComment;
}
