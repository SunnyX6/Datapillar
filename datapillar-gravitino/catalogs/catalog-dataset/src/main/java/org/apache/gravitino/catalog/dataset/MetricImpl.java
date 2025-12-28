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
package org.apache.gravitino.catalog.dataset;

import java.util.Map;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.meta.AuditInfo;

/** Metric 接口的实现类 */
public class MetricImpl implements Metric, Auditable {

  private String name;
  private String code;
  private Type type;
  private String dataType;
  private String unit;
  private String unitName;
  private String comment;
  private Map<String, String> properties;
  private int currentVersion;
  private int lastVersion;
  private AuditInfo auditInfo;

  private MetricImpl() {}

  @Override
  public String name() {
    return name;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public Type type() {
    return type;
  }

  @Override
  public String dataType() {
    return dataType;
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
  public String comment() {
    return comment;
  }

  @Override
  public Map<String, String> properties() {
    return properties;
  }

  @Override
  public int currentVersion() {
    return currentVersion;
  }

  @Override
  public int lastVersion() {
    return lastVersion;
  }

  @Override
  public AuditInfo auditInfo() {
    return auditInfo;
  }

  /** Builder 类用于构建 MetricImpl 实例 */
  public static class Builder {
    private final MetricImpl metric;

    private Builder() {
      metric = new MetricImpl();
    }

    public Builder withName(String name) {
      metric.name = name;
      return this;
    }

    public Builder withCode(String code) {
      metric.code = code;
      return this;
    }

    public Builder withType(Type type) {
      metric.type = type;
      return this;
    }

    public Builder withDataType(String dataType) {
      metric.dataType = dataType;
      return this;
    }

    public Builder withUnit(String unit) {
      metric.unit = unit;
      return this;
    }

    public Builder withUnitName(String unitName) {
      metric.unitName = unitName;
      return this;
    }

    public Builder withComment(String comment) {
      metric.comment = comment;
      return this;
    }

    public Builder withProperties(Map<String, String> properties) {
      metric.properties = properties;
      return this;
    }

    public Builder withCurrentVersion(int currentVersion) {
      metric.currentVersion = currentVersion;
      return this;
    }

    public Builder withLastVersion(int lastVersion) {
      metric.lastVersion = lastVersion;
      return this;
    }

    public Builder withAuditInfo(AuditInfo auditInfo) {
      metric.auditInfo = auditInfo;
      return this;
    }

    public MetricImpl build() {
      return metric;
    }
  }

  public static Builder builder() {
    return new Builder();
  }
}
