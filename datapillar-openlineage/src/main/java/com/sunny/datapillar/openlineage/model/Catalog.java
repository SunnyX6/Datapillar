package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Gravitino catalog row. */
@Data
public class Catalog {
  private Long catalogId;
  private Long metalakeId;
  private String catalogName;
  private String catalogComment;
}
