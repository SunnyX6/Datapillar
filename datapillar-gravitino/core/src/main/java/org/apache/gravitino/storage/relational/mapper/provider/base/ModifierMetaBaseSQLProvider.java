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

import static org.apache.gravitino.storage.relational.mapper.ModifierMetaMapper.TABLE_NAME;

import java.util.List;
import org.apache.gravitino.storage.relational.mapper.provider.TenantSqlSupport;
import org.apache.gravitino.storage.relational.po.ModifierPO;
import org.apache.ibatis.annotations.Param;

/** Modifier Metadata basics SQL Provider */
public class ModifierMetaBaseSQLProvider {

  public String insertModifierMeta(@Param("modifier") ModifierPO modifierPO) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "INSERT INTO "
        + TABLE_NAME
        + " (modifier_id, modifier_name, modifier_code,"
        + " metalake_id, catalog_id, schema_id, modifier_comment, modifier_type,"
        + " audit_info, deleted_at, "
        + TenantSqlSupport.tenantColumn()
        + ")"
        + " VALUES (#{modifier.modifierId}, #{modifier.modifierName},"
        + " #{modifier.modifierCode},"
        + " #{modifier.metalakeId}, #{modifier.catalogId},"
        + " #{modifier.schemaId}, #{modifier.modifierComment},"
        + " #{modifier.modifierType},"
        + " #{modifier.auditInfo}, #{modifier.deletedAt}, "
        + tenantId
        + ")";
  }

  public String insertModifierMetaOnDuplicateKeyUpdate(@Param("modifier") ModifierPO modifierPO) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "INSERT INTO "
        + TABLE_NAME
        + " (modifier_id, modifier_name, modifier_code,"
        + " metalake_id, catalog_id, schema_id, modifier_comment, modifier_type,"
        + " audit_info, deleted_at, "
        + TenantSqlSupport.tenantColumn()
        + ")"
        + " VALUES (#{modifier.modifierId}, #{modifier.modifierName},"
        + " #{modifier.modifierCode},"
        + " #{modifier.metalakeId}, #{modifier.catalogId},"
        + " #{modifier.schemaId}, #{modifier.modifierComment},"
        + " #{modifier.modifierType},"
        + " #{modifier.auditInfo}, #{modifier.deletedAt}, "
        + tenantId
        + ")"
        + " ON DUPLICATE KEY UPDATE"
        + " modifier_name = #{modifier.modifierName},"
        + " modifier_code = #{modifier.modifierCode},"
        + " metalake_id = #{modifier.metalakeId},"
        + " catalog_id = #{modifier.catalogId},"
        + " schema_id = #{modifier.schemaId},"
        + " modifier_comment = #{modifier.modifierComment},"
        + " modifier_type = #{modifier.modifierType},"
        + " audit_info = #{modifier.auditInfo},"
        + " deleted_at = #{modifier.deletedAt}";
  }

  public String listModifierPOsBySchemaId(@Param("schemaId") Long schemaId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT modifier_id AS modifierId, modifier_name AS modifierName, modifier_code AS modifierCode,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, modifier_comment AS modifierComment,"
        + " modifier_type AS modifierType, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String listModifierPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT modifier_id AS modifierId, modifier_name AS modifierName, modifier_code AS modifierCode,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, modifier_comment AS modifierComment,"
        + " modifier_type AS modifierType, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + " ORDER BY modifier_id"
        + " LIMIT #{limit} OFFSET #{offset}";
  }

  public String countModifiersBySchemaId(@Param("schemaId") Long schemaId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT COUNT(*) FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String selectModifierMetaBySchemaIdAndModifierCode(
      @Param("schemaId") Long schemaId, @Param("modifierCode") String modifierCode) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT modifier_id AS modifierId, modifier_name AS modifierName, modifier_code AS modifierCode,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, modifier_comment AS modifierComment,"
        + " modifier_type AS modifierType, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND modifier_code = #{modifierCode} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String listModifierPOsByModifierIds(@Param("modifierIds") List<Long> modifierIds) {
    long tenantId = TenantSqlSupport.requireTenantId();
    StringBuilder sql = new StringBuilder();
    sql.append(
        "SELECT modifier_id AS modifierId, modifier_name AS modifierName, modifier_code AS modifierCode,"
            + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, modifier_comment AS modifierComment,"
            + " modifier_type AS modifierType, audit_info AS auditInfo, deleted_at AS deletedAt"
            + " FROM "
            + TABLE_NAME
            + " WHERE deleted_at = 0 AND "
            + TenantSqlSupport.tenantPredicate(null, tenantId)
            + " AND modifier_id IN (");
    for (int i = 0; i < modifierIds.size(); i++) {
      sql.append("#{modifierIds[").append(i).append("]}");
      if (i < modifierIds.size() - 1) {
        sql.append(", ");
      }
    }
    sql.append(")");
    return sql.toString();
  }

  public String updateModifierMeta(
      @Param("newModifier") ModifierPO newModifierPO,
      @Param("oldModifier") ModifierPO oldModifierPO) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + TABLE_NAME
        + " SET modifier_name = #{newModifier.modifierName},"
        + " modifier_code = #{newModifier.modifierCode},"
        + " metalake_id = #{newModifier.metalakeId},"
        + " catalog_id = #{newModifier.catalogId},"
        + " schema_id = #{newModifier.schemaId},"
        + " modifier_comment = #{newModifier.modifierComment},"
        + " modifier_type = #{newModifier.modifierType},"
        + " audit_info = #{newModifier.auditInfo},"
        + " deleted_at = #{newModifier.deletedAt}"
        + " WHERE modifier_id = #{oldModifier.modifierId}"
        + " AND modifier_name = #{oldModifier.modifierName}"
        + " AND modifier_code = #{oldModifier.modifierCode}"
        + " AND metalake_id = #{oldModifier.metalakeId}"
        + " AND catalog_id = #{oldModifier.catalogId}"
        + " AND schema_id = #{oldModifier.schemaId}"
        + " AND ((modifier_comment = #{oldModifier.modifierComment}) OR "
        + " (modifier_comment IS NULL AND #{oldModifier.modifierComment} IS NULL))"
        + " AND audit_info = #{oldModifier.auditInfo}"
        + " AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }
}
