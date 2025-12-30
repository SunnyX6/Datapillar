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
 * 表变更信息
 *
 * <p>用于在 OpenLineage 事件中传递具体的表/列变更操作
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TableChangeInfo {

  /** 变更类型 */
  public enum ChangeType {
    // 表级别变更
    RENAME_TABLE,
    UPDATE_COMMENT,
    SET_PROPERTY,
    REMOVE_PROPERTY,
    ADD_INDEX,
    DELETE_INDEX,

    // 列级别变更
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

  /** 变更类型 */
  @JsonProperty("type")
  private final ChangeType type;

  /** 新表名（RENAME_TABLE 时使用） */
  @JsonProperty("newName")
  private final String newName;

  /** 新注释（UPDATE_COMMENT / UPDATE_COLUMN_COMMENT 时使用） */
  @JsonProperty("newComment")
  private final String newComment;

  /** 属性键（SET_PROPERTY / REMOVE_PROPERTY 时使用） */
  @JsonProperty("propertyKey")
  private final String propertyKey;

  /** 属性值（SET_PROPERTY 时使用） */
  @JsonProperty("propertyValue")
  private final String propertyValue;

  /** 列名（列级别变更时使用） */
  @JsonProperty("columnName")
  private final String columnName;

  /** 旧列名（RENAME_COLUMN 时使用） */
  @JsonProperty("oldColumnName")
  private final String oldColumnName;

  /** 新列名（RENAME_COLUMN 时使用） */
  @JsonProperty("newColumnName")
  private final String newColumnName;

  /** 列数据类型（ADD_COLUMN / UPDATE_COLUMN_TYPE 时使用） */
  @JsonProperty("dataType")
  private final String dataType;

  /** 列注释（ADD_COLUMN 时使用） */
  @JsonProperty("columnComment")
  private final String columnComment;

  /** 列是否可空（ADD_COLUMN / UPDATE_COLUMN_NULLABILITY 时使用） */
  @JsonProperty("nullable")
  private final Boolean nullable;

  /** 列是否自增（ADD_COLUMN / UPDATE_COLUMN_AUTO_INCREMENT 时使用） */
  @JsonProperty("autoIncrement")
  private final Boolean autoIncrement;

  /** 列默认值（ADD_COLUMN / UPDATE_COLUMN_DEFAULT_VALUE 时使用） */
  @JsonProperty("defaultValue")
  private final String defaultValue;

  /** 列位置（ADD_COLUMN / UPDATE_COLUMN_POSITION 时使用，如 "FIRST" 或 "AFTER xxx"） */
  @JsonProperty("position")
  private final String position;

  /** 索引名（ADD_INDEX / DELETE_INDEX 时使用） */
  @JsonProperty("indexName")
  private final String indexName;

  /** 索引类型（ADD_INDEX 时使用） */
  @JsonProperty("indexType")
  private final String indexType;

  /** 索引列（ADD_INDEX 时使用） */
  @JsonProperty("indexColumns")
  private final String[] indexColumns;
}
