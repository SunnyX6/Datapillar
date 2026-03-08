package com.sunny.datapillar.studio.integration.gravitino.model.request;

import java.util.List;
import lombok.Data;

@Data
public class MetricVersionUpdateCommand {

  private String metricName;

  private String metricCode;

  private String metricType;

  private String dataType;

  private String comment;

  private String unit;

  private String unitName;

  private List<String> parentMetricCodes;

  private String calculationFormula;

  private Long refTableId;

  private String measureColumnIds;

  private String filterColumnIds;
}
