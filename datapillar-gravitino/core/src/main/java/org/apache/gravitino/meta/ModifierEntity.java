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

/** Modifier entity, represents reusable semantic modifiers. */
@ToString
public class ModifierEntity implements Entity, Auditable, HasIdentifier {

  public static final Field ID = Field.required("id", Long.class, "Modifier unique ID");
  public static final Field NAME = Field.required("name", String.class, "The name of the modifier");
  public static final Field NAMESPACE =
      Field.required("namespace", Namespace.class, "Namespace of modifiers");
  public static final Field CODE = Field.required("code", String.class, "Encoding of modifiers");
  public static final Field COMMENT =
      Field.optional("comment", String.class, "Comments on modifiers");
  public static final Field MODIFIER_TYPE =
      Field.optional("modifier_type", String.class, "Type of modifier，from value range");
  public static final Field AUDIT_INFO =
      Field.required("audit_info", AuditInfo.class, "Audit information for modifiers");

  private Long id;
  private String name;
  private Namespace namespace;
  private String code;
  private String comment;
  private String modifierType;
  private AuditInfo auditInfo;

  private ModifierEntity() {}

  @Override
  public Map<Field, Object> fields() {
    Map<Field, Object> fields = Maps.newHashMap();
    fields.put(ID, id);
    fields.put(NAME, name);
    fields.put(NAMESPACE, namespace);
    fields.put(CODE, code);
    fields.put(COMMENT, comment);
    fields.put(MODIFIER_TYPE, modifierType);
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

  public String comment() {
    return comment;
  }

  public String modifierType() {
    return modifierType;
  }

  @Override
  public AuditInfo auditInfo() {
    return auditInfo;
  }

  @Override
  public EntityType type() {
    return EntityType.MODIFIER;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModifierEntity)) return false;
    ModifierEntity that = (ModifierEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(namespace, that.namespace)
        && Objects.equals(code, that.code)
        && Objects.equals(comment, that.comment)
        && Objects.equals(modifierType, that.modifierType)
        && Objects.equals(auditInfo, that.auditInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, namespace, code, comment, modifierType, auditInfo);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final ModifierEntity modifierEntity;

    private Builder() {
      modifierEntity = new ModifierEntity();
    }

    public Builder withId(Long id) {
      modifierEntity.id = id;
      return this;
    }

    public Builder withName(String name) {
      modifierEntity.name = name;
      return this;
    }

    public Builder withNamespace(Namespace namespace) {
      modifierEntity.namespace = namespace;
      return this;
    }

    public Builder withCode(String code) {
      modifierEntity.code = code;
      return this;
    }

    public Builder withComment(String comment) {
      modifierEntity.comment = comment;
      return this;
    }

    public Builder withModifierType(String modifierType) {
      modifierEntity.modifierType = modifierType;
      return this;
    }

    public Builder withAuditInfo(AuditInfo auditInfo) {
      modifierEntity.auditInfo = auditInfo;
      return this;
    }

    public ModifierEntity build() {
      modifierEntity.validate();
      return modifierEntity;
    }
  }
}
