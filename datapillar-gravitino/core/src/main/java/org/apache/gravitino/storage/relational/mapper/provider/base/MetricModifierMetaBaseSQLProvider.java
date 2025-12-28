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
package org.apache.gravitino.storage.relational.mapper.provider.base;

import static org.apache.gravitino.storage.relational.mapper.MetricModifierMetaMapper.TABLE_NAME;

import java.util.List;
import org.apache.gravitino.storage.relational.po.MetricModifierPO;
import org.apache.ibatis.annotations.Param;

/** MetricModifier 元数据基础 SQL Provider */
public class MetricModifierMetaBaseSQLProvider {

  public String listMetricModifierPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return "SELECT modifier_id AS modifierId, modifier_name AS modifierName, modifier_code AS modifierCode,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, modifier_comment AS modifierComment,"
        + " modifier_type AS modifierType, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String listMetricModifierPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit) {
    return "SELECT modifier_id AS modifierId, modifier_name AS modifierName, modifier_code AS modifierCode,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, modifier_comment AS modifierComment,"
        + " modifier_type AS modifierType, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0"
        + " ORDER BY modifier_id"
        + " LIMIT #{limit} OFFSET #{offset}";
  }

  public String countMetricModifiersBySchemaId(@Param("schemaId") Long schemaId) {
    return "SELECT COUNT(*) FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String selectMetricModifierMetaBySchemaIdAndModifierCode(
      @Param("schemaId") Long schemaId, @Param("modifierCode") String modifierCode) {
    return "SELECT modifier_id AS modifierId, modifier_name AS modifierName, modifier_code AS modifierCode,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, modifier_comment AS modifierComment,"
        + " modifier_type AS modifierType, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND modifier_code = #{modifierCode} AND deleted_at = 0";
  }

  public String listMetricModifierPOsByModifierIds(@Param("modifierIds") List<Long> modifierIds) {
    StringBuilder sql = new StringBuilder();
    sql.append(
        "SELECT modifier_id AS modifierId, modifier_name AS modifierName, modifier_code AS modifierCode,"
            + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, modifier_comment AS modifierComment,"
            + " modifier_type AS modifierType, audit_info AS auditInfo, deleted_at AS deletedAt"
            + " FROM "
            + TABLE_NAME
            + " WHERE deleted_at = 0 AND modifier_id IN (");
    for (int i = 0; i < modifierIds.size(); i++) {
      sql.append("#{modifierIds[").append(i).append("]}");
      if (i < modifierIds.size() - 1) {
        sql.append(", ");
      }
    }
    sql.append(")");
    return sql.toString();
  }

  public String updateMetricModifierMeta(
      @Param("newMetricModifier") MetricModifierPO newMetricModifierPO,
      @Param("oldMetricModifier") MetricModifierPO oldMetricModifierPO) {
    return "UPDATE "
        + TABLE_NAME
        + " SET modifier_name = #{newMetricModifier.modifierName},"
        + " modifier_code = #{newMetricModifier.modifierCode},"
        + " metalake_id = #{newMetricModifier.metalakeId},"
        + " catalog_id = #{newMetricModifier.catalogId},"
        + " schema_id = #{newMetricModifier.schemaId},"
        + " modifier_comment = #{newMetricModifier.modifierComment},"
        + " modifier_type = #{newMetricModifier.modifierType},"
        + " audit_info = #{newMetricModifier.auditInfo},"
        + " deleted_at = #{newMetricModifier.deletedAt}"
        + " WHERE modifier_id = #{oldMetricModifier.modifierId}"
        + " AND modifier_name = #{oldMetricModifier.modifierName}"
        + " AND modifier_code = #{oldMetricModifier.modifierCode}"
        + " AND metalake_id = #{oldMetricModifier.metalakeId}"
        + " AND catalog_id = #{oldMetricModifier.catalogId}"
        + " AND schema_id = #{oldMetricModifier.schemaId}"
        + " AND ((modifier_comment = #{oldMetricModifier.modifierComment}) OR "
        + " (modifier_comment IS NULL AND #{oldMetricModifier.modifierComment} IS NULL))"
        + " AND audit_info = #{oldMetricModifier.auditInfo}"
        + " AND deleted_at = 0";
  }
}
