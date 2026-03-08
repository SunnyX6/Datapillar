package com.sunny.datapillar.studio.dto.semantic.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "MetricVersionUpdateRequest")
public class MetricVersionUpdateRequest {

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
