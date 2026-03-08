package com.sunny.datapillar.studio.integration.gravitino.model;

import lombok.Data;

@Data
public class GravitinoUnitResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String code;

  private String name;

  private String symbol;

  private String comment;

  private GravitinoAuditResponse audit;

  private GravitinoOwnerResponse owner;
}
