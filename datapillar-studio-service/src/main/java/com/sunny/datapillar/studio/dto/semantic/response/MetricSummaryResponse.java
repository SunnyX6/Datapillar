package com.sunny.datapillar.studio.dto.semantic.response;

import lombok.Data;

@Data
public class MetricSummaryResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String code;

  private String name;

  private String type;

  private int currentVersion;

  private int lastVersion;
}
