package com.sunny.datapillar.studio.integration.gravitino.model;

import lombok.Data;

@Data
public class GravitinoWordRootResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String code;

  private String name;

  private String dataType;

  private String comment;

  private GravitinoAuditResponse audit;

  private GravitinoOwnerResponse owner;
}
