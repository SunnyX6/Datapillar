/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.The ASF licenses this file
 * to you under the Apache License,Version 2.0 (the
 * "License");you may not use this file except in compliance
 * with the License.You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,* software distributed under the License is distributed on an
 * "AS IS" BASIS,WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND,either express or implied.See the License for the
 * specific language governing permissions and limitations
 * under the License.*/

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
 * Gravitino Customize Dataset Facet
 *
 * <p>used to pass Gravitino unique metadata,Include table description,Properties,Audit
 * information,etc.OpenLineage Standard facet Unsupported fields.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GravitinoDatasetFacet implements OpenLineage.DatasetFacet {

  private static final URI SCHEMA_URL =
      URI.create("https://gravitino.apache.org/spec/facets/GravitinoDatasetFacet.json");

  @Getter(AccessLevel.NONE)
  @JsonProperty("_producer")
  private final URI producer;

  @Getter(AccessLevel.NONE)
  @JsonProperty("_schemaURL")
  private final URI schemaURL;

  @Getter(AccessLevel.NONE)
  @JsonProperty("_deleted")
  private final Boolean deleted;

  /** table/Schema Description */
  @JsonProperty("description")
  private final String description;

  /** tenant ID Snapshot */
  @JsonProperty("tenantId")
  private final Long tenantId;

  /** Tenant coding snapshot */
  @JsonProperty("tenantCode")
  private final String tenantCode;

  /** Tenant name snapshot */
  @JsonProperty("tenantName")
  private final String tenantName;

  /** table/Schema extended attributes */
  @JsonProperty("properties")
  private final Map<String, String> properties;

  /** Partition information(JSON string) */
  @JsonProperty("partitions")
  private final String partitions;

  /** Distribution information(JSON string) */
  @JsonProperty("distribution")
  private final String distribution;

  /** Sort information(JSON string) */
  @JsonProperty("sortOrders")
  private final String sortOrders;

  /** Index information(JSON string) */
  @JsonProperty("indexes")
  private final String indexes;

  /** Creator */
  @JsonProperty("creator")
  private final String creator;

  /** creation time(ISO 8601) */
  @JsonProperty("createTime")
  private final String createTime;

  /** last modified by */
  @JsonProperty("lastModifier")
  private final String lastModifier;

  /** last modified time(ISO 8601) */
  @JsonProperty("lastModifiedTime")
  private final String lastModifiedTime;

  /** Column extension metadata */
  @JsonProperty("columns")
  private final List<GravitinoColumnMetadata> columns;

  /** table change list(alter_table Used during events) */
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

  /** Column extension metadata */
  @Getter
  @Builder
  public static class GravitinoColumnMetadata {
    /** List */
    @JsonProperty("name")
    private final String name;

    /** Is it available? */
    @JsonProperty("nullable")
    private final Boolean nullable;

    /** Whether to increment automatically */
    @JsonProperty("autoIncrement")
    private final Boolean autoIncrement;

    /** default value expression */
    @JsonProperty("defaultValue")
    private final String defaultValue;
  }

  /** create Builder and set default value */
  public static GravitinoDatasetFacetBuilder builder(URI producer) {
    return new GravitinoDatasetFacetBuilder().producer(producer).schemaURL(SCHEMA_URL);
  }

  /** from Audit Information creation audit related fields */
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
