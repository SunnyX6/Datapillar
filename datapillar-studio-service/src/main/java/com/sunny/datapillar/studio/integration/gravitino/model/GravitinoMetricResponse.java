package com.sunny.datapillar.studio.integration.gravitino.model;

import java.util.Map;
import lombok.Data;

@Data
public class GravitinoMetricResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String code;

  private String name;

  private String type;

  private String comment;

  private String dataType;

  private String unit;

  private String unitName;

  private Map<String, String> properties;

  private int currentVersion;

  private int lastVersion;

  private GravitinoAuditResponse audit;

  private GravitinoOwnerResponse owner;
}
