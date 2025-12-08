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
package org.apache.gravitino.storage.relational.po;

import com.google.common.base.Preconditions;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode
@Getter
public class MetricVersionPO {

  private Long metricId;
  private Long metalakeId;
  private Long catalogId;
  private Long schemaId;
  private Integer version;
  private String metricName;
  private String metricCode;
  private String metricType;
  private String metricComment;
  private String metricUnit;
  private String aggregationLogic;
  private String parentMetricIds;
  private String calculationFormula;
  private String versionProperties;
  private String auditInfo;
  private Long deletedAt;

  private MetricVersionPO() {}

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private final MetricVersionPO metricVersionPO;

    private Builder() {
      metricVersionPO = new MetricVersionPO();
    }

    public Builder withMetricId(Long metricId) {
      metricVersionPO.metricId = metricId;
      return this;
    }

    public Builder withMetalakeId(Long metalakeId) {
      metricVersionPO.metalakeId = metalakeId;
      return this;
    }

    public Builder withCatalogId(Long catalogId) {
      metricVersionPO.catalogId = catalogId;
      return this;
    }

    public Builder withSchemaId(Long schemaId) {
      metricVersionPO.schemaId = schemaId;
      return this;
    }

    public Builder withVersion(Integer version) {
      metricVersionPO.version = version;
      return this;
    }

    public Builder withMetricName(String metricName) {
      metricVersionPO.metricName = metricName;
      return this;
    }

    public Builder withMetricCode(String metricCode) {
      metricVersionPO.metricCode = metricCode;
      return this;
    }

    public Builder withMetricType(String metricType) {
      metricVersionPO.metricType = metricType;
      return this;
    }

    public Builder withMetricComment(String metricComment) {
      metricVersionPO.metricComment = metricComment;
      return this;
    }

    public Builder withMetricUnit(String metricUnit) {
      metricVersionPO.metricUnit = metricUnit;
      return this;
    }

    public Builder withAggregationLogic(String aggregationLogic) {
      metricVersionPO.aggregationLogic = aggregationLogic;
      return this;
    }

    public Builder withParentMetricIds(String parentMetricIds) {
      metricVersionPO.parentMetricIds = parentMetricIds;
      return this;
    }

    public Builder withCalculationFormula(String calculationFormula) {
      metricVersionPO.calculationFormula = calculationFormula;
      return this;
    }

    public Builder withVersionProperties(String versionProperties) {
      metricVersionPO.versionProperties = versionProperties;
      return this;
    }

    public Builder withAuditInfo(String auditInfo) {
      metricVersionPO.auditInfo = auditInfo;
      return this;
    }

    public Builder withDeletedAt(Long deletedAt) {
      metricVersionPO.deletedAt = deletedAt;
      return this;
    }

    public MetricVersionPO build() {
      Preconditions.checkArgument(metricVersionPO.metricId != null, "Metric id is required");
      Preconditions.checkArgument(metricVersionPO.metalakeId != null, "Metalake id is required");
      Preconditions.checkArgument(metricVersionPO.catalogId != null, "Catalog id is required");
      Preconditions.checkArgument(metricVersionPO.schemaId != null, "Schema id is required");
      Preconditions.checkArgument(metricVersionPO.version != null, "Version is required");
      Preconditions.checkArgument(
          StringUtils.isNotBlank(metricVersionPO.metricName), "Metric name cannot be empty");
      Preconditions.checkArgument(
          StringUtils.isNotBlank(metricVersionPO.metricCode), "Metric code cannot be empty");
      Preconditions.checkArgument(
          StringUtils.isNotBlank(metricVersionPO.metricType), "Metric type cannot be empty");
      Preconditions.checkArgument(
          StringUtils.isNotBlank(metricVersionPO.auditInfo), "Audit info cannot be empty");
      Preconditions.checkArgument(metricVersionPO.deletedAt != null, "Deleted at is required");
      return metricVersionPO;
    }
  }
}
