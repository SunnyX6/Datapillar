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

import lombok.Getter;

/** Unit PO，对应 unit_meta 表 */
@Getter
public class UnitPO {
  private Long unitId;
  private String unitCode;
  private String unitName;
  private String unitSymbol;
  private Long metalakeId;
  private Long catalogId;
  private Long schemaId;
  private String unitComment;
  private String auditInfo;
  private Long deletedAt;

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final UnitPO unitPO;

    private Builder() {
      unitPO = new UnitPO();
    }

    public Builder withUnitId(Long unitId) {
      unitPO.unitId = unitId;
      return this;
    }

    public Builder withUnitCode(String unitCode) {
      unitPO.unitCode = unitCode;
      return this;
    }

    public Builder withUnitName(String unitName) {
      unitPO.unitName = unitName;
      return this;
    }

    public Builder withUnitSymbol(String unitSymbol) {
      unitPO.unitSymbol = unitSymbol;
      return this;
    }

    public Builder withMetalakeId(Long metalakeId) {
      unitPO.metalakeId = metalakeId;
      return this;
    }

    public Builder withCatalogId(Long catalogId) {
      unitPO.catalogId = catalogId;
      return this;
    }

    public Builder withSchemaId(Long schemaId) {
      unitPO.schemaId = schemaId;
      return this;
    }

    public Builder withUnitComment(String unitComment) {
      unitPO.unitComment = unitComment;
      return this;
    }

    public Builder withAuditInfo(String auditInfo) {
      unitPO.auditInfo = auditInfo;
      return this;
    }

    public Builder withDeletedAt(Long deletedAt) {
      unitPO.deletedAt = deletedAt;
      return this;
    }

    public UnitPO build() {
      return unitPO;
    }
  }
}
