package com.sunny.datapillar.studio.integration.gravitino.model.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class MetricCreateCommand {

  @NotBlank(message = "Metric code cannot be empty")
  private String code;

  @NotBlank(message = "Metric name cannot be empty")
  private String name;

  @NotBlank(message = "Metric type cannot be empty")
  private String type;

  private String dataType;

  private String comment;

  private Map<String, String> properties;

  private String unit;

  private List<String> parentMetricCodes;

  private String calculationFormula;

  private Long refTableId;

  private String refCatalogName;

  private String refSchemaName;

  private String refTableName;

  private String measureColumnIds;

  private String filterColumnIds;
}
