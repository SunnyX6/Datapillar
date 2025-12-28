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
        + " (domain_id, domain_code, domain_name, domain_type, domain_level,"
        + " items, data_type, metalake_id, catalog_id, schema_id, domain_comment, audit_info, deleted_at)"
        + " VALUES (#{domain.domainId}, #{domain.domainCode},"
        + " #{domain.domainName}, #{domain.domainType}, #{domain.domainLevel},"
        + " #{domain.items}, #{domain.dataType}, #{domain.metalakeId}, #{domain.catalogId},"
        + " #{domain.schemaId}, #{domain.domainComment},"
        + " #{domain.auditInfo}, #{domain.deletedAt})";
  }

  public String insertValueDomainMetaOnDuplicateKeyUpdate(@Param("domain") ValueDomainPO domainPO) {
    return "INSERT INTO "
        + TABLE_NAME
        + " (domain_id, domain_code, domain_name, domain_type, domain_level,"
        + " items, data_type, metalake_id, catalog_id, schema_id, domain_comment, audit_info, deleted_at)"
        + " VALUES (#{domain.domainId}, #{domain.domainCode},"
        + " #{domain.domainName}, #{domain.domainType}, #{domain.domainLevel},"
        + " #{domain.items}, #{domain.dataType}, #{domain.metalakeId}, #{domain.catalogId},"
        + " #{domain.schemaId}, #{domain.domainComment},"
        + " #{domain.auditInfo}, #{domain.deletedAt})"
        + " ON DUPLICATE KEY UPDATE"
        + " domain_name = #{domain.domainName},"
        + " domain_type = #{domain.domainType},"
        + " domain_level = #{domain.domainLevel},"
        + " items = #{domain.items},"
        + " data_type = #{domain.dataType},"
        + " domain_comment = #{domain.domainComment},"
        + " audit_info = #{domain.auditInfo},"
        + " deleted_at = #{domain.deletedAt}";
  }

  public String listValueDomainPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return "SELECT domain_id AS domainId, domain_code AS domainCode, domain_name AS domainName,"
        + " domain_type AS domainType, domain_level AS domainLevel, items, data_type AS dataType,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId,"
        + " domain_comment AS domainComment, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String listValueDomainPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit) {
    return "SELECT domain_id AS domainId, domain_code AS domainCode, domain_name AS domainName,"
        + " domain_type AS domainType, domain_level AS domainLevel, items, data_type AS dataType,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId,"
        + " domain_comment AS domainComment, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0"
        + " ORDER BY domain_id"
        + " LIMIT #{limit} OFFSET #{offset}";
  }

  public String countValueDomainsBySchemaId(@Param("schemaId") Long schemaId) {
    return "SELECT COUNT(*) FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String selectValueDomainMetaBySchemaIdAndDomainCode(
      @Param("schemaId") Long schemaId, @Param("domainCode") String domainCode) {
    return "SELECT domain_id AS domainId, domain_code AS domainCode, domain_name AS domainName,"
        + " domain_type AS domainType, domain_level AS domainLevel, items, data_type AS dataType,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId,"
        + " domain_comment AS domainComment, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND domain_code = #{domainCode} AND deleted_at = 0";
  }

  public String selectValueDomainMetaByDomainId(@Param("domainId") Long domainId) {
    return "SELECT domain_id AS domainId, domain_code AS domainCode, domain_name AS domainName,"
        + " domain_type AS domainType, domain_level AS domainLevel, items, data_type AS dataType,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId,"
        + " domain_comment AS domainComment, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE domain_id = #{domainId} AND deleted_at = 0";
  }

  public String listValueDomainPOsByDomainIds(@Param("domainIds") java.util.List<Long> domainIds) {
    StringBuilder sql = new StringBuilder();
    sql.append(
        "SELECT domain_id AS domainId, domain_code AS domainCode, domain_name AS domainName,"
            + " domain_type AS domainType, domain_level AS domainLevel, items, data_type AS dataType,"
            + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId,"
            + " domain_comment AS domainComment, audit_info AS auditInfo, deleted_at AS deletedAt"
            + " FROM ");
    sql.append(TABLE_NAME);
    sql.append(" WHERE domain_id IN (");
    for (int i = 0; i < domainIds.size(); i++) {
      sql.append("#{domainIds[").append(i).append("]}");
      if (i < domainIds.size() - 1) {
        sql.append(", ");
      }
    }
    sql.append(") AND deleted_at = 0");
    return sql.toString();
  }

  public String updateValueDomainMeta(
      @Param("newDomain") ValueDomainPO newDomainPO,
      @Param("oldDomain") ValueDomainPO oldDomainPO) {
    return "UPDATE "
        + TABLE_NAME
        + " SET domain_name = #{newDomain.domainName},"
        + " domain_type = #{newDomain.domainType},"
        + " domain_level = #{newDomain.domainLevel},"
        + " items = #{newDomain.items},"
        + " data_type = #{newDomain.dataType},"
        + " domain_comment = #{newDomain.domainComment},"
        + " audit_info = #{newDomain.auditInfo},"
        + " deleted_at = #{newDomain.deletedAt}"
        + " WHERE domain_id = #{oldDomain.domainId}"
        + " AND deleted_at = 0";
  }

  public String softDeleteValueDomainMetaBySchemaIdAndDomainCode(
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
