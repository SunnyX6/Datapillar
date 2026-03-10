package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Gravitino value domain row. */
@Data
public class ValueDomain {
  private Long domainId;
  private Long metalakeId;
  private Long catalogId;
  private Long schemaId;
  private String domainCode;
  private String domainName;
  private String domainComment;
}
