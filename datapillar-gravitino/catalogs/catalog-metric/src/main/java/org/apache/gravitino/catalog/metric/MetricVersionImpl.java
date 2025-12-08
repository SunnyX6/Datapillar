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
package org.apache.gravitino.catalog.metric;

import java.util.Map;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.meta.AuditInfo;
import org.apache.gravitino.metric.Metric;
import org.apache.gravitino.metric.MetricVersion;

/** MetricVersion 接口的实现类 */
public class MetricVersionImpl implements MetricVersion, Auditable {

  private int version;
  private String name;
  private String code;
  private Metric.Type type;
  private String comment;
  private String unit;
  private String aggregationLogic;
  private Long[] parentMetricIds;
  private String calculationFormula;
  private Map<String, String> properties;
  private AuditInfo auditInfo;

  private MetricVersionImpl() {}

  @Override
  public int version() {
    return version;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public Metric.Type type() {
    return type;
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
  public Map<String, String> properties() {
    return properties;
  }

  @Override
  public AuditInfo auditInfo() {
    return auditInfo;
  }

  /** Builder 类用于构建 MetricVersionImpl 实例 */
  public static class Builder {
    private final MetricVersionImpl metricVersion;

    private Builder() {
      metricVersion = new MetricVersionImpl();
    }

    public Builder withVersion(int version) {
      metricVersion.version = version;
      return this;
    }

    public Builder withName(String name) {
      metricVersion.name = name;
      return this;
    }

    public Builder withCode(String code) {
      metricVersion.code = code;
      return this;
    }

    public Builder withType(Metric.Type type) {
      metricVersion.type = type;
      return this;
    }

    public Builder withComment(String comment) {
      metricVersion.comment = comment;
      return this;
    }

    public Builder withUnit(String unit) {
      metricVersion.unit = unit;
      return this;
    }

    public Builder withAggregationLogic(String aggregationLogic) {
      metricVersion.aggregationLogic = aggregationLogic;
      return this;
    }

    public Builder withParentMetricIds(Long[] parentMetricIds) {
      metricVersion.parentMetricIds = parentMetricIds;
      return this;
    }

    public Builder withCalculationFormula(String calculationFormula) {
      metricVersion.calculationFormula = calculationFormula;
      return this;
    }

    public Builder withProperties(Map<String, String> properties) {
      metricVersion.properties = properties;
      return this;
    }

    public Builder withAuditInfo(AuditInfo auditInfo) {
      metricVersion.auditInfo = auditInfo;
      return this;
    }

    public MetricVersionImpl build() {
      return metricVersion;
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
