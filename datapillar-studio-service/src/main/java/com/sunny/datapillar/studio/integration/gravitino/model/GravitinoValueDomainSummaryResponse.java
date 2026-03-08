package com.sunny.datapillar.studio.integration.gravitino.model;

import lombok.Data;

@Data
public class GravitinoValueDomainSummaryResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String domainCode;

  private String domainName;

  private String domainType;

  private String domainLevel;
}
