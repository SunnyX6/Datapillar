package com.sunny.datapillar.studio.dto.semantic.response;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class MetricVersionResponse {

  private String metalake;

  private String catalogName;

  private String schemaName;

  private String metricCode;

  private Long id;

  private int version;

  private String metricName;

  private String metricType;

  private String comment;

  private String dataType;

  private String unit;

  private String unitName;

  private String unitSymbol;

  private List<String> parentMetricCodes;

  private String calculationFormula;

  private Long refTableId;

  private String refCatalogName;

  private String refSchemaName;

  private String refTableName;

  private String measureColumnIds;

  private String filterColumnIds;

  private Map<String, String> properties;

  private AuditResponse audit;
}
