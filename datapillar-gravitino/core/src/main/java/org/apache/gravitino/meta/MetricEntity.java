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
package org.apache.gravitino.meta;

import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import lombok.ToString;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.Entity;
import org.apache.gravitino.Field;
import org.apache.gravitino.HasIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.dataset.Metric;

@ToString
public class MetricEntity implements Entity, Auditable, HasIdentifier {

  public static final Field ID = Field.required("id", Long.class, "指标实体的唯一 ID");
  public static final Field NAME = Field.required("name", String.class, "指标实体的名称");
  public static final Field CODE = Field.required("code", String.class, "指标实体的编码");
  public static final Field TYPE = Field.required("type", Metric.Type.class, "指标实体的类型");
  public static final Field DATA_TYPE = Field.optional("data_type", String.class, "指标的数据类型");
  public static final Field UNIT = Field.optional("unit", String.class, "指标的单位");
  public static final Field COMMENT = Field.optional("comment", String.class, "指标实体的注释或描述");
  public static final Field CURRENT_VERSION =
      Field.optional("current_version", Integer.class, "指标的当前版本号");
  public static final Field LAST_VERSION =
      Field.optional("last_version", Integer.class, "指标的最新版本号");
  public static final Field PROPERTIES = Field.optional("properties", Map.class, "指标实体的属性");
  public static final Field AUDIT_INFO = Field.required("audit_info", AuditInfo.class, "指标实体的审计信息");

  private Long id;
  private String name;
  private Namespace namespace;
  private String code;
  private Metric.Type type;
  private String dataType;
  private String unit;
  private String unitName;
  private String comment;
  private Integer currentVersion;
  private Integer lastVersion;
  private AuditInfo auditInfo;
  private Map<String, String> properties;

  private MetricEntity() {}

  @Override
  public Map<Field, Object> fields() {
    Map<Field, Object> fields = Maps.newHashMap();
    fields.put(ID, id);
    fields.put(NAME, name);
    fields.put(CODE, code);
    fields.put(TYPE, type);
    fields.put(DATA_TYPE, dataType);
    fields.put(UNIT, unit);
    fields.put(COMMENT, comment);
    fields.put(CURRENT_VERSION, currentVersion);
    fields.put(LAST_VERSION, lastVersion);
    fields.put(PROPERTIES, properties);
    fields.put(AUDIT_INFO, auditInfo);
    return Collections.unmodifiableMap(fields);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Long id() {
    return id;
  }

  @Override
  public Namespace namespace() {
    return namespace;
  }

  public String code() {
    return code;
  }

  public Metric.Type metricType() {
    return type;
  }

  public String dataType() {
    return dataType;
  }

  public String unit() {
    return unit;
  }

  public String unitName() {
    return unitName;
  }

  public String comment() {
    return comment;
  }

  public Integer currentVersion() {
    return currentVersion;
  }

  public Integer lastVersion() {
    return lastVersion;
  }

  public Map<String, String> properties() {
    return properties;
  }

  @Override
  public AuditInfo auditInfo() {
    return auditInfo;
  }

  @Override
  public EntityType type() {
    return EntityType.METRIC;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MetricEntity)) {
      return false;
    }
    MetricEntity that = (MetricEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(code, that.code)
        && type == that.type
        && Objects.equals(dataType, that.dataType)
        && Objects.equals(comment, that.comment)
        && Objects.equals(currentVersion, that.currentVersion)
        && Objects.equals(lastVersion, that.lastVersion)
        && Objects.equals(properties, that.properties)
        && Objects.equals(auditInfo, that.auditInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        name,
        code,
        type,
        dataType,
        comment,
        currentVersion,
        lastVersion,
        properties,
        auditInfo);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final MetricEntity metric;

    private Builder() {
      metric = new MetricEntity();
    }

    public Builder withId(Long id) {
      metric.id = id;
      return this;
    }

    public Builder withName(String name) {
      metric.name = name;
      return this;
    }

    public Builder withNamespace(Namespace namespace) {
      metric.namespace = namespace;
      return this;
    }

    public Builder withCode(String code) {
      metric.code = code;
      return this;
    }

    public Builder withType(Metric.Type type) {
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

    public Builder withCurrentVersion(Integer currentVersion) {
      metric.currentVersion = currentVersion;
      return this;
    }

    public Builder withLastVersion(Integer lastVersion) {
      metric.lastVersion = lastVersion;
      return this;
    }

    public Builder withProperties(Map<String, String> properties) {
      metric.properties = properties;
      return this;
    }

    public Builder withAuditInfo(AuditInfo auditInfo) {
      metric.auditInfo = auditInfo;
      return this;
    }

    public MetricEntity build() {
      metric.validate();
      return metric;
    }
  }
}
