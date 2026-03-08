package com.sunny.datapillar.studio.integration.gravitino.model;

import lombok.Data;

@Data
public class GravitinoMetricVersionSummaryResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String metricCode;

  private int version;
}
