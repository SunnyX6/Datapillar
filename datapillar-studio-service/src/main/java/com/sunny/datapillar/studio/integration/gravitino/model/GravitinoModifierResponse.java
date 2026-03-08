package com.sunny.datapillar.studio.integration.gravitino.model;

import lombok.Data;

@Data
public class GravitinoModifierResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String code;

  private String name;

  private String modifierType;

  private String comment;

  private GravitinoAuditResponse audit;

  private GravitinoOwnerResponse owner;
}
