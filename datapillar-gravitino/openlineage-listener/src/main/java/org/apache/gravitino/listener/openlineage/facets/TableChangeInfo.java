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
import lombok.Builder;
import lombok.Getter;

/**
 * Table change information
 *
 * <p>used in OpenLineage Pass the specific table in the event/Column change operation
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TableChangeInfo {

  /** Change type */
  public enum ChangeType {
    // Table level changes
    RENAME_TABLE,
    UPDATE_COMMENT,
    SET_PROPERTY,
    REMOVE_PROPERTY,
    ADD_INDEX,
    DELETE_INDEX,

    // Column level changes
    ADD_COLUMN,
    DELETE_COLUMN,
    RENAME_COLUMN,
    UPDATE_COLUMN_TYPE,
    UPDATE_COLUMN_COMMENT,
    UPDATE_COLUMN_POSITION,
    UPDATE_COLUMN_NULLABILITY,
    UPDATE_COLUMN_DEFAULT_VALUE,
    UPDATE_COLUMN_AUTO_INCREMENT
  }

  /** Change type */
  @JsonProperty("type")
  private final ChangeType type;

  /** New table name（RENAME_TABLE used when） */
  @JsonProperty("newName")
  private final String newName;

  /** new annotation（UPDATE_COMMENT / UPDATE_COLUMN_COMMENT used when） */
  @JsonProperty("newComment")
  private final String newComment;

  /** property key（SET_PROPERTY / REMOVE_PROPERTY used when） */
  @JsonProperty("propertyKey")
  private final String propertyKey;

  /** attribute value（SET_PROPERTY used when） */
  @JsonProperty("propertyValue")
  private final String propertyValue;

  /** List（Used when column level changes） */
  @JsonProperty("columnName")
  private final String columnName;

  /** old column name（RENAME_COLUMN used when） */
  @JsonProperty("oldColumnName")
  private final String oldColumnName;

  /** New listing（RENAME_COLUMN used when） */
  @JsonProperty("newColumnName")
  private final String newColumnName;

  /** Column data type（ADD_COLUMN / UPDATE_COLUMN_TYPE used when） */
  @JsonProperty("dataType")
  private final String dataType;

  /** Column comments（ADD_COLUMN used when） */
  @JsonProperty("columnComment")
  private final String columnComment;

  /** Whether the column is nullable（ADD_COLUMN / UPDATE_COLUMN_NULLABILITY used when） */
  @JsonProperty("nullable")
  private final Boolean nullable;

  /** Whether the column is auto-incremented（ADD_COLUMN / UPDATE_COLUMN_AUTO_INCREMENT used when） */
  @JsonProperty("autoIncrement")
  private final Boolean autoIncrement;

  /** Column default value（ADD_COLUMN / UPDATE_COLUMN_DEFAULT_VALUE used when） */
  @JsonProperty("defaultValue")
  private final String defaultValue;

  /**
   * column position（ADD_COLUMN / UPDATE_COLUMN_POSITION used when，Such as "FIRST" or "AFTER xxx"）
   */
  @JsonProperty("position")
  private final String position;

  /** Index name（ADD_INDEX / DELETE_INDEX used when） */
  @JsonProperty("indexName")
  private final String indexName;

  /** Index type（ADD_INDEX used when） */
  @JsonProperty("indexType")
  private final String indexType;

  /** Index column（ADD_INDEX used when） */
  @JsonProperty("indexColumns")
  private final String[] indexColumns;
}
