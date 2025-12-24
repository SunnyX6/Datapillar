/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.gravitino.dto.dataset;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.Audit;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dataset.MetricVersion;
import org.apache.gravitino.dto.AuditDTO;

/** 表示指标版本的 DTO (Data Transfer Object) */
@NoArgsConstructor(access = AccessLevel.PRIVATE, force = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class MetricVersionDTO implements MetricVersion {

  @JsonProperty("version")
  private int version;

  @JsonProperty("name")
  private String name;

  @JsonProperty("code")
  private String code;

  @JsonProperty("type")
  private Metric.Type type;

  @JsonProperty("dataType")
  private String dataType;

  @JsonProperty("comment")
  private String comment;

  @JsonProperty("unit")
  private String unit;

  @JsonProperty("aggregationLogic")
  private String aggregationLogic;

  @JsonProperty("parentMetricIds")
  private Long[] parentMetricIds;

  @JsonProperty("calculationFormula")
  private String calculationFormula;

  @JsonProperty("refCatalogName")
  private String refCatalogName;

  @JsonProperty("refSchemaName")
  private String refSchemaName;

  @JsonProperty("refTableName")
  private String refTableName;

  @JsonProperty("measureColumns")
  private String measureColumns;

  @JsonProperty("filterColumns")
  private String filterColumns;

  @JsonProperty("properties")
  private Map<String, String> properties;

  @JsonProperty("audit")
  private AuditDTO audit;

  @Override
  public int version() {
    return version;
  }

  @Override
  public String metricName() {
    return name;
  }

  @Override
  public String metricCode() {
    return code;
  }

  @Override
  public Metric.Type metricType() {
    return type;
  }

  @Override
  public String dataType() {
    return dataType;
  }

  @Override
  public String comment() {
    return comment;
  }

  @Override
  public String unit() {
    return unit;
  }

  @Override
  public String aggregationLogic() {
    return aggregationLogic;
  }

  @Override
  public Long[] parentMetricIds() {
    return parentMetricIds;
  }

  @Override
  public String calculationFormula() {
    return calculationFormula;
  }

  @Override
  public String refCatalogName() {
    return refCatalogName;
  }

  @Override
  public String refSchemaName() {
    return refSchemaName;
  }

  @Override
  public String refTableName() {
    return refTableName;
  }

  @Override
  public String measureColumns() {
    return measureColumns;
  }

  @Override
  public String filterColumns() {
    return filterColumns;
  }

  @Override
  public Map<String, String> properties() {
    return properties;
  }

  @Override
  public Audit auditInfo() {
    return audit;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for constructing a Metric Version DTO. */
  public static class Builder {
    private int version;
    private String name;
    private String code;
    private Metric.Type type;
    private String dataType;
    private String comment;
    private String unit;
    private String aggregationLogic;
    private Long[] parentMetricIds;
    private String calculationFormula;
    private String refCatalogName;
    private String refSchemaName;
    private String refTableName;
    private String measureColumns;
    private String filterColumns;
    private Map<String, String> properties;
    private AuditDTO audit;

    public Builder withVersion(int version) {
      this.version = version;
      return this;
    }

    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    public Builder withCode(String code) {
      this.code = code;
      return this;
    }

    public Builder withType(Metric.Type type) {
      this.type = type;
      return this;
    }

    public Builder withDataType(String dataType) {
      this.dataType = dataType;
      return this;
    }

    public Builder withComment(String comment) {
      this.comment = comment;
      return this;
    }

    public Builder withUnit(String unit) {
      this.unit = unit;
      return this;
    }

    public Builder withAggregationLogic(String aggregationLogic) {
      this.aggregationLogic = aggregationLogic;
      return this;
    }

    public Builder withParentMetricIds(Long[] parentMetricIds) {
      this.parentMetricIds = parentMetricIds;
      return this;
    }

    public Builder withCalculationFormula(String calculationFormula) {
      this.calculationFormula = calculationFormula;
      return this;
    }

    public Builder withRefCatalogName(String refCatalogName) {
      this.refCatalogName = refCatalogName;
      return this;
    }

    public Builder withRefSchemaName(String refSchemaName) {
      this.refSchemaName = refSchemaName;
      return this;
    }

    public Builder withRefTableName(String refTableName) {
      this.refTableName = refTableName;
      return this;
    }

    public Builder withMeasureColumns(String measureColumns) {
      this.measureColumns = measureColumns;
      return this;
    }

    public Builder withFilterColumns(String filterColumns) {
      this.filterColumns = filterColumns;
      return this;
    }

    public Builder withProperties(Map<String, String> properties) {
      this.properties = properties;
      return this;
    }

    public Builder withAudit(AuditDTO audit) {
      this.audit = audit;
      return this;
    }

    public MetricVersionDTO build() {
      Preconditions.checkArgument(version >= 0, "version cannot be negative");
      Preconditions.checkArgument(StringUtils.isNotBlank(name), "name cannot be null or empty");
      Preconditions.checkArgument(StringUtils.isNotBlank(code), "code cannot be null or empty");
      Preconditions.checkArgument(type != null, "type cannot be null");
      Preconditions.checkArgument(audit != null, "audit cannot be null");

      return new MetricVersionDTO(
          version,
          name,
          code,
          type,
          dataType,
          comment,
          unit,
          aggregationLogic,
          parentMetricIds,
          calculationFormula,
          refCatalogName,
          refSchemaName,
          refTableName,
          measureColumns,
          filterColumns,
          properties,
          audit);
    }
  }
}
