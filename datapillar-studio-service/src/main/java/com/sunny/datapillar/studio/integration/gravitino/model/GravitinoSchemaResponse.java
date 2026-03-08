package com.sunny.datapillar.studio.integration.gravitino.model;

import java.util.Map;
import lombok.Data;

@Data
public class GravitinoSchemaResponse {

  private String metalake;

  private String catalogName;

  private String name;

  private String comment;

  private Map<String, String> properties;

  private GravitinoAuditResponse audit;

  private GravitinoOwnerResponse owner;
}
