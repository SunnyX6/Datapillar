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
public class MetricPO {

  private Long metricId;
  private String metricName;
  private String metricCode;
  private String metricType;
  private String dataType;
  private String unit;
  private String unitName;
  private Long metalakeId;
  private Long catalogId;
  private Long schemaId;
  private String metricComment;
  private Integer currentVersion;
  private Integer lastVersion;
  private String auditInfo;
  private Long deletedAt;

  private MetricPO() {}

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private final MetricPO metricPO;

    private Builder() {
      metricPO = new MetricPO();
    }

    public Builder withMetricId(Long metricId) {
      metricPO.metricId = metricId;
      return this;
    }

    public Builder withMetricName(String metricName) {
      metricPO.metricName = metricName;
      return this;
    }

    public Builder withMetricCode(String metricCode) {
      metricPO.metricCode = metricCode;
      return this;
    }

    public Builder withMetricType(String metricType) {
      metricPO.metricType = metricType;
      return this;
    }

    public Builder withDataType(String dataType) {
      metricPO.dataType = dataType;
      return this;
    }

    public Builder withUnit(String unit) {
      metricPO.unit = unit;
      return this;
    }

    public Builder withUnitName(String unitName) {
      metricPO.unitName = unitName;
      return this;
    }

    public Builder withMetalakeId(Long metalakeId) {
      metricPO.metalakeId = metalakeId;
      return this;
    }

    public Builder withCatalogId(Long catalogId) {
      metricPO.catalogId = catalogId;
      return this;
    }

    public Builder withSchemaId(Long schemaId) {
      metricPO.schemaId = schemaId;
      return this;
    }

    public Builder withMetricComment(String metricComment) {
      metricPO.metricComment = metricComment;
      return this;
    }

    public Builder withCurrentVersion(Integer currentVersion) {
      metricPO.currentVersion = currentVersion;
      return this;
    }

    public Builder withLastVersion(Integer lastVersion) {
      metricPO.lastVersion = lastVersion;
      return this;
    }

    public Builder withAuditInfo(String auditInfo) {
      metricPO.auditInfo = auditInfo;
      return this;
    }

    public Builder withDeletedAt(Long deletedAt) {
      metricPO.deletedAt = deletedAt;
      return this;
    }

    public MetricPO build() {
      Preconditions.checkArgument(metricPO.metricId != null, "Metric id is required");
      Preconditions.checkArgument(
          StringUtils.isNotBlank(metricPO.metricName), "Metric name cannot be empty");
      Preconditions.checkArgument(
          StringUtils.isNotBlank(metricPO.metricCode), "Metric code cannot be empty");
      Preconditions.checkArgument(
          StringUtils.isNotBlank(metricPO.metricType), "Metric type cannot be empty");
      Preconditions.checkArgument(metricPO.metalakeId != null, "Metalake id is required");
      Preconditions.checkArgument(metricPO.catalogId != null, "Catalog id is required");
      Preconditions.checkArgument(metricPO.schemaId != null, "Schema id is required");
      Preconditions.checkArgument(
          StringUtils.isNotBlank(metricPO.auditInfo), "Audit info cannot be empty");
      Preconditions.checkArgument(metricPO.deletedAt != null, "Deleted at is required");
      return metricPO;
    }
  }
}
