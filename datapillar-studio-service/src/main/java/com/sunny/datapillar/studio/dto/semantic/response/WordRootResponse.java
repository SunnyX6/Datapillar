package com.sunny.datapillar.studio.dto.semantic.response;

import lombok.Data;

@Data
public class WordRootResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String code;

  private String name;

  private String dataType;

  private String comment;

  private AuditResponse audit;

  private OwnerResponse owner;
}
