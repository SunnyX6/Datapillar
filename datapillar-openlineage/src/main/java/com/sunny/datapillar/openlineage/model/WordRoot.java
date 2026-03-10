package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Gravitino word root row. */
@Data
public class WordRoot {
  private Long rootId;
  private Long metalakeId;
  private Long catalogId;
  private Long schemaId;
  private String rootCode;
  private String rootName;
  private String rootComment;
}
