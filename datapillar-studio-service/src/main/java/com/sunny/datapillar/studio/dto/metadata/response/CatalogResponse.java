package com.sunny.datapillar.studio.dto.metadata.response;

import java.util.Map;
import lombok.Data;

@Data
public class CatalogResponse {

  private String metalake;

  private String name;

  private String type;

  private String provider;

  private String comment;

  private Map<String, String> properties;

  private AuditResponse audit;

  private OwnerResponse owner;
}
