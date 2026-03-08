package com.sunny.datapillar.studio.dto.metadata.response;

import java.util.Map;
import lombok.Data;

@Data
public class SchemaResponse {

  private String metalake;

  private String catalogName;

  private String name;

  private String comment;

  private Map<String, String> properties;

  private AuditResponse audit;

  private OwnerResponse owner;
}
