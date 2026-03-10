package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Gravitino modifier row. */
@Data
public class Modifier {
  private Long modifierId;
  private Long metalakeId;
  private Long catalogId;
  private Long schemaId;
  private String modifierCode;
  private String modifierName;
  private String modifierComment;
}
