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

/** MetricRoot PO，对应 metric_root_meta 表 */
@Getter
public class MetricRootPO {
  private Long rootId;
  private String rootCode;
  private String rootNameCn;
  private String rootNameEn;
  private Long metalakeId;
  private Long catalogId;
  private Long schemaId;
  private String rootComment;
  private String auditInfo;
  private Long deletedAt;

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final MetricRootPO metricRootPO;

    private Builder() {
      metricRootPO = new MetricRootPO();
    }

    public Builder withRootId(Long rootId) {
      metricRootPO.rootId = rootId;
      return this;
    }

    public Builder withRootCode(String rootCode) {
      metricRootPO.rootCode = rootCode;
      return this;
    }

    public Builder withRootNameCn(String rootNameCn) {
      metricRootPO.rootNameCn = rootNameCn;
      return this;
    }

    public Builder withRootNameEn(String rootNameEn) {
      metricRootPO.rootNameEn = rootNameEn;
      return this;
    }

    public Builder withMetalakeId(Long metalakeId) {
      metricRootPO.metalakeId = metalakeId;
      return this;
    }

    public Builder withCatalogId(Long catalogId) {
      metricRootPO.catalogId = catalogId;
      return this;
    }

    public Builder withSchemaId(Long schemaId) {
      metricRootPO.schemaId = schemaId;
      return this;
    }

    public Builder withRootComment(String rootComment) {
      metricRootPO.rootComment = rootComment;
      return this;
    }

    public Builder withAuditInfo(String auditInfo) {
      metricRootPO.auditInfo = auditInfo;
      return this;
    }

    public Builder withDeletedAt(Long deletedAt) {
      metricRootPO.deletedAt = deletedAt;
      return this;
    }

    public MetricRootPO build() {
      return metricRootPO;
    }
  }
}
