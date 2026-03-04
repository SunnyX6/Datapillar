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

import static org.apache.gravitino.storage.relational.mapper.WordRootMetaMapper.TABLE_NAME;

import org.apache.gravitino.storage.relational.mapper.provider.TenantSqlSupport;
import org.apache.gravitino.storage.relational.po.WordRootPO;
import org.apache.ibatis.annotations.Param;

/** WordRoot Metadata basics SQL Provider */
public class WordRootMetaBaseSQLProvider {

  public String insertWordRootMeta(@Param("wordRoot") WordRootPO wordRootPO) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "INSERT INTO "
        + TABLE_NAME
        + " (root_id, root_code, root_name, data_type,"
        + " metalake_id, catalog_id, schema_id, root_comment,"
        + " audit_info, deleted_at, "
        + TenantSqlSupport.tenantColumn()
        + ")"
        + " VALUES (#{wordRoot.rootId}, #{wordRoot.rootCode},"
        + " #{wordRoot.rootName}, #{wordRoot.dataType},"
        + " #{wordRoot.metalakeId}, #{wordRoot.catalogId},"
        + " #{wordRoot.schemaId}, #{wordRoot.rootComment},"
        + " #{wordRoot.auditInfo}, #{wordRoot.deletedAt}, "
        + tenantId
        + ")";
  }

  public String insertWordRootMetaOnDuplicateKeyUpdate(@Param("wordRoot") WordRootPO wordRootPO) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "INSERT INTO "
        + TABLE_NAME
        + " (root_id, root_code, root_name, data_type,"
        + " metalake_id, catalog_id, schema_id, root_comment,"
        + " audit_info, deleted_at, "
        + TenantSqlSupport.tenantColumn()
        + ")"
        + " VALUES (#{wordRoot.rootId}, #{wordRoot.rootCode},"
        + " #{wordRoot.rootName}, #{wordRoot.dataType},"
        + " #{wordRoot.metalakeId}, #{wordRoot.catalogId},"
        + " #{wordRoot.schemaId}, #{wordRoot.rootComment},"
        + " #{wordRoot.auditInfo}, #{wordRoot.deletedAt}, "
        + tenantId
        + ")"
        + " ON DUPLICATE KEY UPDATE"
        + " root_code = #{wordRoot.rootCode},"
        + " root_name = #{wordRoot.rootName},"
        + " data_type = #{wordRoot.dataType},"
        + " metalake_id = #{wordRoot.metalakeId},"
        + " catalog_id = #{wordRoot.catalogId},"
        + " schema_id = #{wordRoot.schemaId},"
        + " root_comment = #{wordRoot.rootComment},"
        + " audit_info = #{wordRoot.auditInfo},"
        + " deleted_at = #{wordRoot.deletedAt}";
  }

  public String listWordRootPOsBySchemaId(@Param("schemaId") Long schemaId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT root_id AS rootId, root_code AS rootCode, root_name AS rootName,"
        + " data_type AS dataType, metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, root_comment AS rootComment,"
        + " audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String listWordRootPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT root_id AS rootId, root_code AS rootCode, root_name AS rootName,"
        + " data_type AS dataType, metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, root_comment AS rootComment,"
        + " audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + " ORDER BY root_id"
        + " LIMIT #{limit} OFFSET #{offset}";
  }

  public String countWordRootsBySchemaId(@Param("schemaId") Long schemaId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT COUNT(*) FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String selectWordRootMetaBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT root_id AS rootId, root_code AS rootCode, root_name AS rootName,"
        + " data_type AS dataType, metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, root_comment AS rootComment,"
        + " audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND root_code = #{rootCode} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String selectWordRootIdBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT root_id FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND root_code = #{rootCode} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String updateWordRootMeta(
      @Param("newWordRoot") WordRootPO newWordRootPO,
      @Param("oldWordRoot") WordRootPO oldWordRootPO) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + TABLE_NAME
        + " SET root_code = #{newWordRoot.rootCode},"
        + " root_name = #{newWordRoot.rootName},"
        + " data_type = #{newWordRoot.dataType},"
        + " metalake_id = #{newWordRoot.metalakeId},"
        + " catalog_id = #{newWordRoot.catalogId},"
        + " schema_id = #{newWordRoot.schemaId},"
        + " root_comment = #{newWordRoot.rootComment},"
        + " audit_info = #{newWordRoot.auditInfo},"
        + " deleted_at = #{newWordRoot.deletedAt}"
        + " WHERE root_id = #{oldWordRoot.rootId}"
        + " AND root_code = #{oldWordRoot.rootCode}"
        + " AND root_name = #{oldWordRoot.rootName}"
        + " AND metalake_id = #{oldWordRoot.metalakeId}"
        + " AND catalog_id = #{oldWordRoot.catalogId}"
        + " AND schema_id = #{oldWordRoot.schemaId}"
        + " AND ((root_comment = #{oldWordRoot.rootComment}) OR "
        + " (root_comment IS NULL AND #{oldWordRoot.rootComment} IS NULL))"
        + " AND audit_info = #{oldWordRoot.auditInfo}"
        + " AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String softDeleteWordRootMetaBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND root_code = #{rootCode} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String softDeleteWordRootMetasBySchemaId(@Param("schemaId") Long schemaId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String softDeleteWordRootMetasByCatalogId(@Param("catalogId") Long catalogId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE catalog_id = #{catalogId} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String softDeleteWordRootMetasByMetalakeId(@Param("metalakeId") Long metalakeId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE metalake_id = #{metalakeId} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String deleteWordRootMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "DELETE FROM "
        + TABLE_NAME
        + " WHERE deleted_at > 0 AND deleted_at < #{legacyTimeline}"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + " LIMIT #{limit}";
  }
}
