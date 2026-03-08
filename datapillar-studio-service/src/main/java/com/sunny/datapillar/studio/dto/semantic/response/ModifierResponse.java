package com.sunny.datapillar.studio.dto.semantic.response;

import lombok.Data;

@Data
public class ModifierResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String code;

  private String name;

  private String modifierType;

  private String comment;

  private AuditResponse audit;

  private OwnerResponse owner;
}
