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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openlineage.client.OpenLineage;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * Gravitino Tag Facet
 *
 * <p>用于传递 Tag 关联信息，包括对象类型、添加的 Tag、移除的 Tag、最终关联的 Tag 列表。
 */
@Getter
@Builder
public class GravitinoTagFacet implements OpenLineage.DatasetFacet {

  private static final URI SCHEMA_URL =
      URI.create("https://datapillar.io/spec/facets/GravitinoTagFacet.json");

  @Getter(AccessLevel.NONE)
  @JsonProperty("_producer")
  private final URI producer;

  @Getter(AccessLevel.NONE)
  @JsonProperty("_schemaURL")
  private final URI schemaURL;

  @Getter(AccessLevel.NONE)
  @JsonProperty("_deleted")
  private final Boolean deleted;

  /** 对象类型：CATALOG, SCHEMA, TABLE, COLUMN */
  @JsonProperty("objectType")
  private final String objectType;

  /** 本次添加的 Tag */
  @JsonProperty("tagsToAdd")
  private final String[] tagsToAdd;

  /** 本次移除的 Tag */
  @JsonProperty("tagsToRemove")
  private final String[] tagsToRemove;

  /** 操作后关联的所有 Tag */
  @JsonProperty("associatedTags")
  private final String[] associatedTags;

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

  /** 创建 Builder 并设置默认值 */
  public static GravitinoTagFacetBuilder builder(URI producer) {
    return new GravitinoTagFacetBuilder().producer(producer).schemaURL(SCHEMA_URL);
  }
}
