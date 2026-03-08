package com.sunny.datapillar.studio.integration.gravitino.model;

import java.util.List;
import lombok.Data;

@Data
public class GravitinoValueDomainResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String domainCode;

  private String domainName;

  private String domainType;

  private String domainLevel;

  private List<GravitinoValueDomainItemResponse> items;

  private String comment;

  private String dataType;

  private GravitinoAuditResponse audit;

  private GravitinoOwnerResponse owner;
}
