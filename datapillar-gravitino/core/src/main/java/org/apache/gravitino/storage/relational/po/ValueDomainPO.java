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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 值域持久化对象 */
@Getter
@ToString
@EqualsAndHashCode
public class ValueDomainPO {
  private Long itemId;
  private String domainCode;
  private String domainName;
  private String domainType;
  private String itemValue;
  private String itemLabel;
  private Long metalakeId;
  private Long catalogId;
  private Long schemaId;
  private String domainComment;
  private String auditInfo;
  private Long deletedAt;

  private ValueDomainPO() {}

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final ValueDomainPO po = new ValueDomainPO();

    public Builder withItemId(Long itemId) {
      po.itemId = itemId;
      return this;
    }

    public Builder withDomainCode(String domainCode) {
      po.domainCode = domainCode;
      return this;
    }

    public Builder withDomainName(String domainName) {
      po.domainName = domainName;
      return this;
    }

    public Builder withDomainType(String domainType) {
      po.domainType = domainType;
      return this;
    }

    public Builder withItemValue(String itemValue) {
      po.itemValue = itemValue;
      return this;
    }

    public Builder withItemLabel(String itemLabel) {
      po.itemLabel = itemLabel;
      return this;
    }

    public Builder withMetalakeId(Long metalakeId) {
      po.metalakeId = metalakeId;
      return this;
    }

    public Builder withCatalogId(Long catalogId) {
      po.catalogId = catalogId;
      return this;
    }

    public Builder withSchemaId(Long schemaId) {
      po.schemaId = schemaId;
      return this;
    }

    public Builder withDomainComment(String domainComment) {
      po.domainComment = domainComment;
      return this;
    }

    public Builder withAuditInfo(String auditInfo) {
      po.auditInfo = auditInfo;
      return this;
    }

    public Builder withDeletedAt(Long deletedAt) {
      po.deletedAt = deletedAt;
      return this;
    }

    public ValueDomainPO build() {
      return po;
    }
  }
}
