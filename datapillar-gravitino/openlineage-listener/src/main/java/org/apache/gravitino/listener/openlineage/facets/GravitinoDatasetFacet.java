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

package org.apache.gravitino.listener.openlineage.facets;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openlineage.client.OpenLineage;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * Gravitino 自定义 Dataset Facet
 *
 * <p>用于传递 Gravitino 特有的元数据，包括表描述、属性、审计信息等 OpenLineage 标准 facet 不支持的字段。
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GravitinoDatasetFacet implements OpenLineage.DatasetFacet {

  private static final URI SCHEMA_URL =
      URI.create("https://datapillar.io/spec/facets/GravitinoDatasetFacet.json");

  @Getter(AccessLevel.NONE)
  @JsonProperty("_producer")
  private final URI producer;

  @Getter(AccessLevel.NONE)
  @JsonProperty("_schemaURL")
  private final URI schemaURL;

  @Getter(AccessLevel.NONE)
  @JsonProperty("_deleted")
  private final Boolean deleted;

  /** 表/Schema 的描述 */
  @JsonProperty("description")
  private final String description;

  /** 租户 ID 快照 */
  @JsonProperty("tenantId")
  private final Long tenantId;

  /** 租户编码快照 */
  @JsonProperty("tenantCode")
  private final String tenantCode;

  /** 租户名称快照 */
  @JsonProperty("tenantName")
  private final String tenantName;

  /** 表/Schema 的扩展属性 */
  @JsonProperty("properties")
  private final Map<String, String> properties;

  /** 分区信息（JSON 字符串） */
  @JsonProperty("partitions")
  private final String partitions;

  /** 分布信息（JSON 字符串） */
  @JsonProperty("distribution")
  private final String distribution;

  /** 排序信息（JSON 字符串） */
  @JsonProperty("sortOrders")
  private final String sortOrders;

  /** 索引信息（JSON 字符串） */
  @JsonProperty("indexes")
  private final String indexes;

  /** 创建者 */
  @JsonProperty("creator")
  private final String creator;

  /** 创建时间（ISO 8601） */
  @JsonProperty("createTime")
  private final String createTime;

  /** 最后修改者 */
  @JsonProperty("lastModifier")
  private final String lastModifier;

  /** 最后修改时间（ISO 8601） */
  @JsonProperty("lastModifiedTime")
  private final String lastModifiedTime;

  /** 列扩展元数据 */
  @JsonProperty("columns")
  private final List<GravitinoColumnMetadata> columns;

  /** 表变更列表（alter_table 事件时使用） */
  @JsonProperty("changes")
  private final List<TableChangeInfo> changes;

  @Override
  public URI get_producer() {
    return producer;
  }

  @Override
  public URI get_schemaURL() {
    return schemaURL != null ? schemaURL : SCHEMA_URL;
  }

  @Override
  public Boolean get_deleted() {
    return deleted;
  }

  @Override
  public Map<String, Object> getAdditionalProperties() {
    return new HashMap<>();
  }

  /** 列扩展元数据 */
  @Getter
  @Builder
  public static class GravitinoColumnMetadata {
    /** 列名 */
    @JsonProperty("name")
    private final String name;

    /** 是否可空 */
    @JsonProperty("nullable")
    private final Boolean nullable;

    /** 是否自增 */
    @JsonProperty("autoIncrement")
    private final Boolean autoIncrement;

    /** 默认值表达式 */
    @JsonProperty("defaultValue")
    private final String defaultValue;
  }

  /** 创建 Builder 并设置默认值 */
  public static GravitinoDatasetFacetBuilder builder(URI producer) {
    return new GravitinoDatasetFacetBuilder().producer(producer).schemaURL(SCHEMA_URL);
  }

  /** 从 Audit 信息创建审计相关字段 */
  public static GravitinoDatasetFacetBuilder fromAudit(
      GravitinoDatasetFacetBuilder builder, org.apache.gravitino.Audit audit) {
    if (audit == null) {
      return builder;
    }
    return builder
        .creator(audit.creator())
        .createTime(formatInstant(audit.createTime()))
        .lastModifier(audit.lastModifier())
        .lastModifiedTime(formatInstant(audit.lastModifiedTime()));
  }

  private static String formatInstant(Instant instant) {
    return instant != null ? instant.toString() : null;
  }
}
