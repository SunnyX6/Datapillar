package com.sunny.datapillar.studio.integration.gravitino.model;

import java.util.Map;
import lombok.Data;

@Data
public class GravitinoCatalogResponse {

  private String metalake;

  private String name;

  private String type;

  private String provider;

  private String comment;

  private Map<String, String> properties;

  private GravitinoAuditResponse audit;

  private GravitinoOwnerResponse owner;
}
