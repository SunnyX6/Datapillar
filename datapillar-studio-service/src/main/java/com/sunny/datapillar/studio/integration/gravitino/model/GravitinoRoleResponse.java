package com.sunny.datapillar.studio.integration.gravitino.model;

import java.util.Map;
import lombok.Data;

@Data
public class GravitinoRoleResponse {

  private String metalake;

  private String name;

  private Map<String, String> properties;

  private GravitinoAuditResponse audit;
}
