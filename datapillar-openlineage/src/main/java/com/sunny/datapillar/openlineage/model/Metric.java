package com.sunny.datapillar.openlineage.model;

import lombok.Data;

/** Gravitino metric row. */
@Data
public class Metric {
  private Long metricId;
  private Long metalakeId;
  private Long catalogId;
  private Long schemaId;
  private String metricName;
  private String metricCode;
  private String metricType;
  private String metricComment;
}
