package com.sunny.datapillar.studio.dto.semantic.response;

import java.util.Map;
import lombok.Data;

@Data
public class MetricResponse {

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

  private AuditResponse audit;

  private OwnerResponse owner;
}
