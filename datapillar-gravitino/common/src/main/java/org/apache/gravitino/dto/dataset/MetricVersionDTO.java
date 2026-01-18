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

  @JsonProperty("id")
  private Long id;

  @JsonProperty("version")
  private Integer version;

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

  @JsonProperty("unitName")
  private String unitName;

  @JsonProperty("unitSymbol")
  private String unitSymbol;

  @JsonProperty("parentMetricCodes")
  private String[] parentMetricCodes;

  @JsonProperty("calculationFormula")
  private String calculationFormula;

  @JsonProperty("refTableId")
  private Long refTableId;

  @JsonProperty("refCatalogName")
  private String refCatalogName;

  @JsonProperty("refSchemaName")
  private String refSchemaName;

  @JsonProperty("refTableName")
  private String refTableName;

  @JsonProperty("measureColumnIds")
  private String measureColumnIds;

  @JsonProperty("filterColumnIds")
  private String filterColumnIds;

  @JsonProperty("properties")
  private Map<String, String> properties;

  @JsonProperty("audit")
  private AuditDTO audit;

  @Override
  public Long id() {
    return id;
  }

  @Override
  public Integer version() {
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
  public String unitName() {
    return unitName;
  }

  @Override
  public String unitSymbol() {
    return unitSymbol;
  }

  @Override
  public String[] parentMetricCodes() {
    return parentMetricCodes;
  }

  @Override
  public String calculationFormula() {
    return calculationFormula;
  }

  @Override
  public Long refTableId() {
    return refTableId;
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
  public String measureColumnIds() {
    return measureColumnIds;
  }

  @Override
  public String filterColumnIds() {
    return filterColumnIds;
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
    private Long id;
    private Integer version;
    private String name;
    private String code;
    private Metric.Type type;
    private String dataType;
    private String comment;
    private String unit;
    private String unitName;
    private String unitSymbol;
    private String[] parentMetricCodes;
    private String calculationFormula;
    private Long refTableId;
    private String refCatalogName;
    private String refSchemaName;
    private String refTableName;
    private String measureColumnIds;
    private String filterColumnIds;
    private Map<String, String> properties;
    private AuditDTO audit;

    public Builder withId(Long id) {
      this.id = id;
      return this;
    }

    public Builder withVersion(Integer version) {
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

    public Builder withUnitName(String unitName) {
      this.unitName = unitName;
      return this;
    }

    public Builder withUnitSymbol(String unitSymbol) {
      this.unitSymbol = unitSymbol;
      return this;
    }

    public Builder withParentMetricCodes(String[] parentMetricCodes) {
      this.parentMetricCodes = parentMetricCodes;
      return this;
    }

    public Builder withCalculationFormula(String calculationFormula) {
      this.calculationFormula = calculationFormula;
      return this;
    }

    public Builder withRefTableId(Long refTableId) {
      this.refTableId = refTableId;
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

    public Builder withMeasureColumnIds(String measureColumnIds) {
      this.measureColumnIds = measureColumnIds;
      return this;
    }

    public Builder withFilterColumnIds(String filterColumnIds) {
      this.filterColumnIds = filterColumnIds;
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
      Preconditions.checkArgument(version != null && version >= 1, "version must be >= 1");
      Preconditions.checkArgument(StringUtils.isNotBlank(name), "name cannot be null or empty");
      Preconditions.checkArgument(StringUtils.isNotBlank(code), "code cannot be null or empty");
      Preconditions.checkArgument(type != null, "type cannot be null");
      Preconditions.checkArgument(audit != null, "audit cannot be null");

      return new MetricVersionDTO(
          id,
          version,
          name,
          code,
          type,
          dataType,
          comment,
          unit,
          unitName,
          unitSymbol,
          parentMetricCodes,
          calculationFormula,
          refTableId,
          refCatalogName,
          refSchemaName,
          refTableName,
          measureColumnIds,
          filterColumnIds,
          properties,
          audit);
    }
  }
}
