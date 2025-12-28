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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.ToString;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.Entity;
import org.apache.gravitino.Field;
import org.apache.gravitino.HasIdentifier;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.dataset.ValueDomain;

/** 值域实体类 */
@ToString
public class ValueDomainEntity implements Entity, Auditable, HasIdentifier, ValueDomain {

  public static final Field ID = Field.required("id", Long.class, "值域 ID");
  public static final Field DOMAIN_CODE = Field.required("domainCode", String.class, "值域编码");
  public static final Field DOMAIN_NAME = Field.required("domainName", String.class, "值域名称");
  public static final Field DOMAIN_TYPE = Field.required("domainType", Type.class, "值域类型");
  public static final Field DOMAIN_LEVEL = Field.required("domainLevel", Level.class, "值域级别");
  public static final Field ITEMS = Field.optional("items", List.class, "值域项列表");
  public static final Field COMMENT = Field.optional("comment", String.class, "注释");
  public static final Field DATA_TYPE = Field.optional("dataType", String.class, "数据类型");
  public static final Field AUDIT_INFO = Field.required("auditInfo", AuditInfo.class, "审计信息");

  private Long id;
  private String domainCode;
  private String domainName;
  private Type domainType;
  private Level domainLevel;
  private List<Item> items;
  private String comment;
  private String dataType;
  private AuditInfo auditInfo;
  private Namespace namespace;

  private ValueDomainEntity() {}

  @Override
  public Long id() {
    return id;
  }

  @Override
  public String name() {
    return domainCode;
  }

  @Override
  public String domainCode() {
    return domainCode;
  }

  @Override
  public String domainName() {
    return domainName;
  }

  @Override
  public Type domainType() {
    return domainType;
  }

  @Override
  public Level domainLevel() {
    return domainLevel;
  }

  @Override
  public List<Item> items() {
    return items;
  }

  @Override
  public String comment() {
    return comment;
  }

  @Override
  public String dataType() {
    return dataType;
  }

  @Override
  public AuditInfo auditInfo() {
    return auditInfo;
  }

  @Override
  public Namespace namespace() {
    return namespace;
  }

  @Override
  public NameIdentifier nameIdentifier() {
    return NameIdentifier.of(namespace, domainCode);
  }

  @Override
  public Map<Field, Object> fields() {
    return Collections.emptyMap();
  }

  @Override
  public EntityType type() {
    return EntityType.VALUE_DOMAIN;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ValueDomainEntity)) return false;
    ValueDomainEntity that = (ValueDomainEntity) o;
    return Objects.equals(id, that.id)
        && Objects.equals(domainCode, that.domainCode)
        && Objects.equals(domainName, that.domainName)
        && domainType == that.domainType
        && domainLevel == that.domainLevel
        && Objects.equals(items, that.items)
        && Objects.equals(comment, that.comment)
        && Objects.equals(dataType, that.dataType)
        && Objects.equals(auditInfo, that.auditInfo)
        && Objects.equals(namespace, that.namespace);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        domainCode,
        domainName,
        domainType,
        domainLevel,
        items,
        comment,
        dataType,
        auditInfo,
        namespace);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final ValueDomainEntity entity = new ValueDomainEntity();

    public Builder withId(Long id) {
      entity.id = id;
      return this;
    }

    public Builder withDomainCode(String domainCode) {
      entity.domainCode = domainCode;
      return this;
    }

    public Builder withDomainName(String domainName) {
      entity.domainName = domainName;
      return this;
    }

    public Builder withDomainType(Type domainType) {
      entity.domainType = domainType;
      return this;
    }

    public Builder withDomainLevel(Level domainLevel) {
      entity.domainLevel = domainLevel;
      return this;
    }

    public Builder withItems(List<Item> items) {
      entity.items = items;
      return this;
    }

    public Builder withComment(String comment) {
      entity.comment = comment;
      return this;
    }

    public Builder withDataType(String dataType) {
      entity.dataType = dataType;
      return this;
    }

    public Builder withAuditInfo(AuditInfo auditInfo) {
      entity.auditInfo = auditInfo;
      return this;
    }

    public Builder withNamespace(Namespace namespace) {
      entity.namespace = namespace;
      return this;
    }

    public ValueDomainEntity build() {
      entity.validate();
      return entity;
    }
  }
}
