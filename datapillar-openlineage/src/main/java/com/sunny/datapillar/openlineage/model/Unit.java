package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Gravitino unit row. */
@Data
public class Unit {
  private Long unitId;
  private Long metalakeId;
  private Long catalogId;
  private Long schemaId;
  private String unitCode;
  private String unitName;
  private String unitComment;
}
