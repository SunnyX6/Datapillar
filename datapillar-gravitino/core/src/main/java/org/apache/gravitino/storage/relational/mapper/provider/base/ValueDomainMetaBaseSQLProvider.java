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

import static org.apache.gravitino.storage.relational.mapper.ValueDomainMetaMapper.TABLE_NAME;

import org.apache.gravitino.storage.relational.po.ValueDomainPO;
import org.apache.ibatis.annotations.Param;

/** ValueDomain 元数据基础 SQL Provider */
public class ValueDomainMetaBaseSQLProvider {

  public String insertValueDomainMeta(@Param("domain") ValueDomainPO domainPO) {
    return "INSERT INTO "
        + TABLE_NAME
        + " (item_id, domain_code, domain_name, domain_type,"
        + " item_value, item_label, metalake_id, catalog_id,"
        + " schema_id, domain_comment, audit_info, deleted_at)"
        + " VALUES (#{domain.itemId}, #{domain.domainCode},"
        + " #{domain.domainName}, #{domain.domainType},"
        + " #{domain.itemValue}, #{domain.itemLabel},"
        + " #{domain.metalakeId}, #{domain.catalogId},"
        + " #{domain.schemaId}, #{domain.domainComment},"
        + " #{domain.auditInfo}, #{domain.deletedAt})";
  }

  public String insertValueDomainMetaOnDuplicateKeyUpdate(@Param("domain") ValueDomainPO domainPO) {
    return "INSERT INTO "
        + TABLE_NAME
        + " (item_id, domain_code, domain_name, domain_type,"
        + " item_value, item_label, metalake_id, catalog_id,"
        + " schema_id, domain_comment, audit_info, deleted_at)"
        + " VALUES (#{domain.itemId}, #{domain.domainCode},"
        + " #{domain.domainName}, #{domain.domainType},"
        + " #{domain.itemValue}, #{domain.itemLabel},"
        + " #{domain.metalakeId}, #{domain.catalogId},"
        + " #{domain.schemaId}, #{domain.domainComment},"
        + " #{domain.auditInfo}, #{domain.deletedAt})"
        + " ON DUPLICATE KEY UPDATE"
        + " domain_name = #{domain.domainName},"
        + " domain_type = #{domain.domainType},"
        + " item_label = #{domain.itemLabel},"
        + " domain_comment = #{domain.domainComment},"
        + " audit_info = #{domain.auditInfo},"
        + " deleted_at = #{domain.deletedAt}";
  }

  public String listValueDomainPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return "SELECT item_id AS itemId, domain_code AS domainCode, domain_name AS domainName,"
        + " domain_type AS domainType, item_value AS itemValue, item_label AS itemLabel,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId,"
        + " domain_comment AS domainComment, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String listValueDomainPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit) {
    return "SELECT item_id AS itemId, domain_code AS domainCode, domain_name AS domainName,"
        + " domain_type AS domainType, item_value AS itemValue, item_label AS itemLabel,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId,"
        + " domain_comment AS domainComment, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0"
        + " ORDER BY item_id"
        + " LIMIT #{limit} OFFSET #{offset}";
  }

  public String countValueDomainsBySchemaId(@Param("schemaId") Long schemaId) {
    return "SELECT COUNT(*) FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String selectValueDomainMetaBySchemaIdAndDomainCodeAndItemValue(
      @Param("schemaId") Long schemaId,
      @Param("domainCode") String domainCode,
      @Param("itemValue") String itemValue) {
    return "SELECT item_id AS itemId, domain_code AS domainCode, domain_name AS domainName,"
        + " domain_type AS domainType, item_value AS itemValue, item_label AS itemLabel,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId,"
        + " domain_comment AS domainComment, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND domain_code = #{domainCode}"
        + " AND item_value = #{itemValue} AND deleted_at = 0";
  }

  public String listValueDomainPOsBySchemaIdAndDomainCode(
      @Param("schemaId") Long schemaId, @Param("domainCode") String domainCode) {
    return "SELECT item_id AS itemId, domain_code AS domainCode, domain_name AS domainName,"
        + " domain_type AS domainType, item_value AS itemValue, item_label AS itemLabel,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId,"
        + " domain_comment AS domainComment, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND domain_code = #{domainCode} AND deleted_at = 0";
  }

  public String updateValueDomainMeta(
      @Param("newDomain") ValueDomainPO newDomainPO,
      @Param("oldDomain") ValueDomainPO oldDomainPO) {
    return "UPDATE "
        + TABLE_NAME
        + " SET domain_name = #{newDomain.domainName},"
        + " domain_type = #{newDomain.domainType},"
        + " item_label = #{newDomain.itemLabel},"
        + " domain_comment = #{newDomain.domainComment},"
        + " audit_info = #{newDomain.auditInfo},"
        + " deleted_at = #{newDomain.deletedAt}"
        + " WHERE item_id = #{oldDomain.itemId}"
        + " AND domain_code = #{oldDomain.domainCode}"
        + " AND item_value = #{oldDomain.itemValue}"
        + " AND deleted_at = 0";
  }

  public String softDeleteValueDomainMetaBySchemaIdAndDomainCodeAndItemValue(
      @Param("schemaId") Long schemaId,
      @Param("domainCode") String domainCode,
      @Param("itemValue") String itemValue) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND domain_code = #{domainCode}"
        + " AND item_value = #{itemValue} AND deleted_at = 0";
  }

  public String softDeleteValueDomainMetasBySchemaIdAndDomainCode(
      @Param("schemaId") Long schemaId, @Param("domainCode") String domainCode) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND domain_code = #{domainCode} AND deleted_at = 0";
  }

  public String softDeleteValueDomainMetasBySchemaId(@Param("schemaId") Long schemaId) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String softDeleteValueDomainMetasByCatalogId(@Param("catalogId") Long catalogId) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE catalog_id = #{catalogId} AND deleted_at = 0";
  }

  public String softDeleteValueDomainMetasByMetalakeId(@Param("metalakeId") Long metalakeId) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE metalake_id = #{metalakeId} AND deleted_at = 0";
  }

  public String deleteValueDomainMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    return "DELETE FROM "
        + TABLE_NAME
        + " WHERE deleted_at > 0 AND deleted_at < #{legacyTimeline} LIMIT #{limit}";
  }
}
