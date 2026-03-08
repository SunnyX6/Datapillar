package com.sunny.datapillar.studio.integration.gravitino.model;

import java.util.Map;
import lombok.Data;

@Data
public class GravitinoTagResponse {

  private String metalake;

  private String name;

  private String comment;

  private Map<String, String> properties;

  private GravitinoAuditResponse audit;
}
