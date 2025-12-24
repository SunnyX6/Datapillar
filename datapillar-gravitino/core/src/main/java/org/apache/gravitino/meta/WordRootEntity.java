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

/** WordRoot 实体，表示词根 */
@ToString
public class WordRootEntity implements Entity, Auditable, HasIdentifier {

  public static final Field ID = Field.required("id", Long.class, "词根的唯一 ID");
  public static final Field CODE = Field.required("code", String.class, "词根的编码");
  public static final Field NAMESPACE = Field.required("namespace", Namespace.class, "词根的命名空间");
  public static final Field ROOT_NAME = Field.required("root_name", String.class, "词根的名称");
  public static final Field DATA_TYPE = Field.optional("data_type", String.class, "词根的数据类型");
  public static final Field COMMENT = Field.optional("comment", String.class, "词根的注释");
  public static final Field AUDIT_INFO = Field.required("audit_info", AuditInfo.class, "词根的审计信息");

  private Long id;
  private String code;
  private Namespace namespace;
  private String rootName;
  private String dataType;
  private String comment;
  private AuditInfo auditInfo;

  private WordRootEntity() {}

  @Override
  public Map<Field, Object> fields() {
    Map<Field, Object> fields = Maps.newHashMap();
    fields.put(ID, id);
    fields.put(CODE, code);
    fields.put(NAMESPACE, namespace);
    fields.put(ROOT_NAME, rootName);
    fields.put(DATA_TYPE, dataType);
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
    return code;
  }

  @Override
  public Namespace namespace() {
    return namespace;
  }

  public String code() {
    return code;
  }

  public String rootName() {
    return rootName;
  }

  public String dataType() {
    return dataType;
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
    return EntityType.WORDROOT;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof WordRootEntity)) return false;
    WordRootEntity that = (WordRootEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(code, that.code)
        && Objects.equals(namespace, that.namespace)
        && Objects.equals(rootName, that.rootName)
        && Objects.equals(dataType, that.dataType)
        && Objects.equals(comment, that.comment)
        && Objects.equals(auditInfo, that.auditInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, code, namespace, rootName, dataType, comment, auditInfo);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final WordRootEntity wordRootEntity;

    private Builder() {
      wordRootEntity = new WordRootEntity();
    }

    public Builder withId(Long id) {
      wordRootEntity.id = id;
      return this;
    }

    public Builder withCode(String code) {
      wordRootEntity.code = code;
      return this;
    }

    public Builder withNamespace(Namespace namespace) {
      wordRootEntity.namespace = namespace;
      return this;
    }

    public Builder withRootName(String rootName) {
      wordRootEntity.rootName = rootName;
      return this;
    }

    public Builder withDataType(String dataType) {
      wordRootEntity.dataType = dataType;
      return this;
    }

    public Builder withComment(String comment) {
      wordRootEntity.comment = comment;
      return this;
    }

    public Builder withAuditInfo(AuditInfo auditInfo) {
      wordRootEntity.auditInfo = auditInfo;
      return this;
    }

    public WordRootEntity build() {
      wordRootEntity.validate();
      return wordRootEntity;
    }
  }
}
