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
 * <p>used to pass Tag Related information,Include object type,added Tag,removed Tag,ultimately
 * associated Tag list.
 */
@Getter
@Builder
public class GravitinoTagFacet implements OpenLineage.DatasetFacet {

  private static final URI SCHEMA_URL =
      URI.create("https://gravitino.apache.org/spec/facets/GravitinoTagFacet.json");

  @Getter(AccessLevel.NONE)
  @JsonProperty("_producer")
  private final URI producer;

  @Getter(AccessLevel.NONE)
  @JsonProperty("_schemaURL")
  private final URI schemaURL;

  @Getter(AccessLevel.NONE)
  @JsonProperty("_deleted")
  private final Boolean deleted;

  /** Object type:CATALOG,SCHEMA,TABLE,COLUMN */
  @JsonProperty("objectType")
  private final String objectType;

  /** Added this time Tag */
  @JsonProperty("tagsToAdd")
  private final String[] tagsToAdd;

  /** Removed this time Tag */
  @JsonProperty("tagsToRemove")
  private final String[] tagsToRemove;

  /** All associated after the operation Tag */
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

  /** create Builder and set default value */
  public static GravitinoTagFacetBuilder builder(URI producer) {
    return new GravitinoTagFacetBuilder().producer(producer).schemaURL(SCHEMA_URL);
  }
}
