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

/** MetricModifier PO，对应 metric_modifier_meta 表 */
@Getter
public class MetricModifierPO {
  private Long modifierId;
  private String modifierName;
  private String modifierCode;
  private String modifierType;
  private Long metalakeId;
  private Long catalogId;
  private Long schemaId;
  private String modifierComment;
  private String auditInfo;
  private Long deletedAt;

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final MetricModifierPO metricModifierPO;

    private Builder() {
      metricModifierPO = new MetricModifierPO();
    }

    public Builder withModifierId(Long modifierId) {
      metricModifierPO.modifierId = modifierId;
      return this;
    }

    public Builder withModifierName(String modifierName) {
      metricModifierPO.modifierName = modifierName;
      return this;
    }

    public Builder withModifierCode(String modifierCode) {
      metricModifierPO.modifierCode = modifierCode;
      return this;
    }

    public Builder withModifierType(String modifierType) {
      metricModifierPO.modifierType = modifierType;
      return this;
    }

    public Builder withMetalakeId(Long metalakeId) {
      metricModifierPO.metalakeId = metalakeId;
      return this;
    }

    public Builder withCatalogId(Long catalogId) {
      metricModifierPO.catalogId = catalogId;
      return this;
    }

    public Builder withSchemaId(Long schemaId) {
      metricModifierPO.schemaId = schemaId;
      return this;
    }

    public Builder withModifierComment(String modifierComment) {
      metricModifierPO.modifierComment = modifierComment;
      return this;
    }

    public Builder withAuditInfo(String auditInfo) {
      metricModifierPO.auditInfo = auditInfo;
      return this;
    }

    public Builder withDeletedAt(Long deletedAt) {
      metricModifierPO.deletedAt = deletedAt;
      return this;
    }

    public MetricModifierPO build() {
      return metricModifierPO;
    }
  }
}
