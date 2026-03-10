package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Gravitino tag relation row. */
@Data
public class TagRelation {
  private Long tagId;
  private Long metadataObjectId;
  private String metadataObjectType;
}
