package com.sunny.datapillar.studio.dto.semantic.response;

import lombok.Data;

@Data
public class ModifierSummaryResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String code;

  private String name;

  private String modifierType;
}
