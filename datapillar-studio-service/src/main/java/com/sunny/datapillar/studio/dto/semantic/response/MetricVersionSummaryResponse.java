package com.sunny.datapillar.studio.dto.semantic.response;

import lombok.Data;

@Data
public class MetricVersionSummaryResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String metricCode;

  private int version;
}
