package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Gravitino tag row. */
@Data
public class Tag {
  private Long tagId;
  private Long metalakeId;
  private String tagName;
  private String comment;
}
