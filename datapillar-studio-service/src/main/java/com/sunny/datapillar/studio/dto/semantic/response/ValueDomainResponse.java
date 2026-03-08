package com.sunny.datapillar.studio.dto.semantic.response;

import java.util.List;
import lombok.Data;

@Data
public class ValueDomainResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String domainCode;

  private String domainName;

  private String domainType;

  private String domainLevel;

  private List<ValueDomainItemResponse> items;

  private String comment;

  private String dataType;

  private AuditResponse audit;

  private OwnerResponse owner;
}
