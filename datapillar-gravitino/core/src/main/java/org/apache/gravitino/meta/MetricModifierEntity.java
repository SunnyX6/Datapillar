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
import java.util.Map;
import java.util.Objects;
import lombok.ToString;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.Entity;
import org.apache.gravitino.Field;
import org.apache.gravitino.HasIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.dataset.MetricModifier;

/** MetricModifier 实体，表示指标修饰符 */
@ToString
public class MetricModifierEntity implements Entity, Auditable, HasIdentifier {

  public static final Field ID = Field.required("id", Long.class, "指标修饰符的唯一 ID");
  public static final Field NAME = Field.required("name", String.class, "指标修饰符的名称");
  public static final Field NAMESPACE = Field.required("namespace", Namespace.class, "指标修饰符的命名空间");
  public static final Field CODE = Field.required("code", String.class, "指标修饰符的编码");
  public static final Field TYPE = Field.required("type", MetricModifier.Type.class, "指标修饰符的类型");
  public static final Field COMMENT = Field.optional("comment", String.class, "指标修饰符的注释");
  public static final Field AUDIT_INFO =
      Field.required("audit_info", AuditInfo.class, "指标修饰符的审计信息");

  private Long id;
  private String name;
  private Namespace namespace;
  private String code;
  private MetricModifier.Type type;
  private String comment;
  private AuditInfo auditInfo;

  private MetricModifierEntity() {}

  @Override
  public Map<Field, Object> fields() {
    Map<Field, Object> fields = Maps.newHashMap();
    fields.put(ID, id);
    fields.put(NAME, name);
    fields.put(NAMESPACE, namespace);
    fields.put(CODE, code);
    fields.put(TYPE, type);
    fields.put(COMMENT, comment);
    fields.put(AUDIT_INFO, auditInfo);
    return fields;
  }

  @Override
  public Long id() {
    return id;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Namespace namespace() {
    return namespace;
  }

  public String code() {
    return code;
  }

  public MetricModifier.Type modifierType() {
    return type;
  }

  public String comment() {
    return comment;
  }

  @Override
  public AuditInfo auditInfo() {
    return auditInfo;
  }

  @Override
  public EntityType type() {
    return EntityType.METRIC_MODIFIER;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MetricModifierEntity)) return false;
    MetricModifierEntity that = (MetricModifierEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(namespace, that.namespace)
        && Objects.equals(code, that.code)
        && type == that.type
        && Objects.equals(comment, that.comment)
        && Objects.equals(auditInfo, that.auditInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, namespace, code, type, comment, auditInfo);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final MetricModifierEntity metricModifierEntity;

    private Builder() {
      metricModifierEntity = new MetricModifierEntity();
    }

    public Builder withId(Long id) {
      metricModifierEntity.id = id;
      return this;
    }

    public Builder withName(String name) {
      metricModifierEntity.name = name;
      return this;
    }

    public Builder withNamespace(Namespace namespace) {
      metricModifierEntity.namespace = namespace;
      return this;
    }

    public Builder withCode(String code) {
      metricModifierEntity.code = code;
      return this;
    }

    public Builder withType(MetricModifier.Type type) {
      metricModifierEntity.type = type;
      return this;
    }

    public Builder withComment(String comment) {
      metricModifierEntity.comment = comment;
      return this;
    }

    public Builder withAuditInfo(AuditInfo auditInfo) {
      metricModifierEntity.auditInfo = auditInfo;
      return this;
    }

    public MetricModifierEntity build() {
      metricModifierEntity.validate();
      return metricModifierEntity;
    }
  }
}
