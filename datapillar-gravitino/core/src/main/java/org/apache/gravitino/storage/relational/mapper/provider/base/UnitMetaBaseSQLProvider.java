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

import static org.apache.gravitino.storage.relational.mapper.UnitMetaMapper.TABLE_NAME;

import org.apache.gravitino.storage.relational.po.UnitPO;
import org.apache.ibatis.annotations.Param;

/** Unit 元数据基础 SQL Provider */
public class UnitMetaBaseSQLProvider {

  public String insertUnitMeta(@Param("unit") UnitPO unitPO) {
    return "INSERT INTO "
        + TABLE_NAME
        + " (unit_id, unit_code, unit_name, unit_symbol,"
        + " metalake_id, catalog_id, schema_id, unit_comment,"
        + " audit_info, deleted_at)"
        + " VALUES (#{unit.unitId}, #{unit.unitCode},"
        + " #{unit.unitName}, #{unit.unitSymbol},"
        + " #{unit.metalakeId}, #{unit.catalogId},"
        + " #{unit.schemaId}, #{unit.unitComment},"
        + " #{unit.auditInfo}, #{unit.deletedAt})";
  }

  public String insertUnitMetaOnDuplicateKeyUpdate(@Param("unit") UnitPO unitPO) {
    return "INSERT INTO "
        + TABLE_NAME
        + " (unit_id, unit_code, unit_name, unit_symbol,"
        + " metalake_id, catalog_id, schema_id, unit_comment,"
        + " audit_info, deleted_at)"
        + " VALUES (#{unit.unitId}, #{unit.unitCode},"
        + " #{unit.unitName}, #{unit.unitSymbol},"
        + " #{unit.metalakeId}, #{unit.catalogId},"
        + " #{unit.schemaId}, #{unit.unitComment},"
        + " #{unit.auditInfo}, #{unit.deletedAt})"
        + " ON DUPLICATE KEY UPDATE"
        + " unit_code = #{unit.unitCode},"
        + " unit_name = #{unit.unitName},"
        + " unit_symbol = #{unit.unitSymbol},"
        + " metalake_id = #{unit.metalakeId},"
        + " catalog_id = #{unit.catalogId},"
        + " schema_id = #{unit.schemaId},"
        + " unit_comment = #{unit.unitComment},"
        + " audit_info = #{unit.auditInfo},"
        + " deleted_at = #{unit.deletedAt}";
  }

  public String listUnitPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return "SELECT unit_id AS unitId, unit_code AS unitCode, unit_name AS unitName,"
        + " unit_symbol AS unitSymbol, metalake_id AS metalakeId, catalog_id AS catalogId,"
        + " schema_id AS schemaId, unit_comment AS unitComment,"
        + " audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String listUnitPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit) {
    return "SELECT unit_id AS unitId, unit_code AS unitCode, unit_name AS unitName,"
        + " unit_symbol AS unitSymbol, metalake_id AS metalakeId, catalog_id AS catalogId,"
        + " schema_id AS schemaId, unit_comment AS unitComment,"
        + " audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0"
        + " ORDER BY unit_id"
        + " LIMIT #{limit} OFFSET #{offset}";
  }

  public String countUnitsBySchemaId(@Param("schemaId") Long schemaId) {
    return "SELECT COUNT(*) FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String selectUnitMetaBySchemaIdAndUnitCode(
      @Param("schemaId") Long schemaId, @Param("unitCode") String unitCode) {
    return "SELECT unit_id AS unitId, unit_code AS unitCode, unit_name AS unitName,"
        + " unit_symbol AS unitSymbol, metalake_id AS metalakeId, catalog_id AS catalogId,"
        + " schema_id AS schemaId, unit_comment AS unitComment,"
        + " audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND unit_code = #{unitCode} AND deleted_at = 0";
  }

  public String selectUnitIdBySchemaIdAndUnitCode(
      @Param("schemaId") Long schemaId, @Param("unitCode") String unitCode) {
    return "SELECT unit_id FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND unit_code = #{unitCode} AND deleted_at = 0";
  }

  public String updateUnitMeta(
      @Param("newUnit") UnitPO newUnitPO, @Param("oldUnit") UnitPO oldUnitPO) {
    return "UPDATE "
        + TABLE_NAME
        + " SET unit_code = #{newUnit.unitCode},"
        + " unit_name = #{newUnit.unitName},"
        + " unit_symbol = #{newUnit.unitSymbol},"
        + " metalake_id = #{newUnit.metalakeId},"
        + " catalog_id = #{newUnit.catalogId},"
        + " schema_id = #{newUnit.schemaId},"
        + " unit_comment = #{newUnit.unitComment},"
        + " audit_info = #{newUnit.auditInfo},"
        + " deleted_at = #{newUnit.deletedAt}"
        + " WHERE unit_id = #{oldUnit.unitId}"
        + " AND unit_code = #{oldUnit.unitCode}"
        + " AND unit_name = #{oldUnit.unitName}"
        + " AND metalake_id = #{oldUnit.metalakeId}"
        + " AND catalog_id = #{oldUnit.catalogId}"
        + " AND schema_id = #{oldUnit.schemaId}"
        + " AND ((unit_comment = #{oldUnit.unitComment}) OR "
        + " (unit_comment IS NULL AND #{oldUnit.unitComment} IS NULL))"
        + " AND audit_info = #{oldUnit.auditInfo}"
        + " AND deleted_at = 0";
  }

  public String softDeleteUnitMetaBySchemaIdAndUnitCode(
      @Param("schemaId") Long schemaId, @Param("unitCode") String unitCode) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND unit_code = #{unitCode} AND deleted_at = 0";
  }

  public String softDeleteUnitMetasBySchemaId(@Param("schemaId") Long schemaId) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String softDeleteUnitMetasByCatalogId(@Param("catalogId") Long catalogId) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE catalog_id = #{catalogId} AND deleted_at = 0";
  }

  public String softDeleteUnitMetasByMetalakeId(@Param("metalakeId") Long metalakeId) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE metalake_id = #{metalakeId} AND deleted_at = 0";
  }

  public String deleteUnitMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    return "DELETE FROM "
        + TABLE_NAME
        + " WHERE deleted_at > 0 AND deleted_at < #{legacyTimeline} LIMIT #{limit}";
  }
}
