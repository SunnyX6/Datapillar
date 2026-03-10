package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Gravitino metric current version row. */
@Data
public class MetricVersion {
  private Long metricId;
  private Long refTableId;
  private String measureColumnIds;
  private String filterColumnIds;
  private String parentMetricCodes;
}
