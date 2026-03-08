package com.sunny.datapillar.studio.dto.semantic.response;

import lombok.Data;

@Data
public class ValueDomainSummaryResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String domainCode;

  private String domainName;

  private String domainType;

  private String domainLevel;
}
