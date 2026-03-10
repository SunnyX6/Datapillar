package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Gravitino schema row. */
@Data
public class Schema {
  private Long schemaId;
  private Long metalakeId;
  private Long catalogId;
  private String schemaName;
  private String schemaComment;
}
