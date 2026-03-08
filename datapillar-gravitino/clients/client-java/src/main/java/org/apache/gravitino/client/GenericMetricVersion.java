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
package org.apache.gravitino.client;

import java.util.Map;
import org.apache.gravitino.Audit;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dataset.MetricVersion;
import org.apache.gravitino.dto.dataset.MetricVersionDTO;

class GenericMetricVersion implements MetricVersion {

  private final MetricVersionDTO metricVersionDTO;

  GenericMetricVersion(MetricVersionDTO metricVersionDTO) {
    this.metricVersionDTO = metricVersionDTO;
  }

  @Override
  public Long id() {
    return metricVersionDTO.id();
  }

  @Override
  public Integer version() {
    return metricVersionDTO.version();
  }

  @Override
  public String metricName() {
    return metricVersionDTO.metricName();
  }

  @Override
  public String metricCode() {
    return metricVersionDTO.metricCode();
  }

  @Override
  public Metric.Type metricType() {
    return metricVersionDTO.metricType();
  }

  @Override
  public String comment() {
    return metricVersionDTO.comment();
  }

  @Override
  public String dataType() {
    return metricVersionDTO.dataType();
  }

  @Override
  public String unit() {
    return metricVersionDTO.unit();
  }

  @Override
  public String unitName() {
    return metricVersionDTO.unitName();
  }

  @Override
  public String unitSymbol() {
    return metricVersionDTO.unitSymbol();
  }

  @Override
  public String[] parentMetricCodes() {
    return metricVersionDTO.parentMetricCodes();
  }

  @Override
  public String calculationFormula() {
    return metricVersionDTO.calculationFormula();
  }

  @Override
  public Long refTableId() {
    return metricVersionDTO.refTableId();
  }

  @Override
  public String refCatalogName() {
    return metricVersionDTO.refCatalogName();
  }

  @Override
  public String refSchemaName() {
    return metricVersionDTO.refSchemaName();
  }

  @Override
  public String refTableName() {
    return metricVersionDTO.refTableName();
  }

  @Override
  public String measureColumnIds() {
    return metricVersionDTO.measureColumnIds();
  }

  @Override
  public String filterColumnIds() {
    return metricVersionDTO.filterColumnIds();
  }

  @Override
  public Map<String, String> properties() {
    return metricVersionDTO.properties();
  }

  @Override
  public Audit auditInfo() {
    return metricVersionDTO.auditInfo();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GenericMetricVersion)) {
      return false;
    }
    GenericMetricVersion that = (GenericMetricVersion) o;
    return metricVersionDTO.equals(that.metricVersionDTO);
  }

  @Override
  public int hashCode() {
    return metricVersionDTO.hashCode();
  }
}
