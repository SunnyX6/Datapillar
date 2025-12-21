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

import org.apache.gravitino.storage.relational.po.WordRootPO;
import org.apache.ibatis.annotations.Param;

/** WordRoot 元数据基础 SQL Provider */
public class WordRootMetaBaseSQLProvider {

  public String insertWordRootMeta(@Param("wordRoot") WordRootPO wordRootPO) {
    return "INSERT INTO "
        + TABLE_NAME
        + " (root_id, root_code, root_name_cn, root_name_en,"
        + " metalake_id, catalog_id, schema_id, root_comment,"
        + " audit_info, deleted_at)"
        + " VALUES (#{wordRoot.rootId}, #{wordRoot.rootCode},"
        + " #{wordRoot.rootNameCn}, #{wordRoot.rootNameEn},"
        + " #{wordRoot.metalakeId}, #{wordRoot.catalogId},"
        + " #{wordRoot.schemaId}, #{wordRoot.rootComment},"
        + " #{wordRoot.auditInfo}, #{wordRoot.deletedAt})";
  }

  public String insertWordRootMetaOnDuplicateKeyUpdate(@Param("wordRoot") WordRootPO wordRootPO) {
    return "INSERT INTO "
        + TABLE_NAME
        + " (root_id, root_code, root_name_cn, root_name_en,"
        + " metalake_id, catalog_id, schema_id, root_comment,"
        + " audit_info, deleted_at)"
        + " VALUES (#{wordRoot.rootId}, #{wordRoot.rootCode},"
        + " #{wordRoot.rootNameCn}, #{wordRoot.rootNameEn},"
        + " #{wordRoot.metalakeId}, #{wordRoot.catalogId},"
        + " #{wordRoot.schemaId}, #{wordRoot.rootComment},"
        + " #{wordRoot.auditInfo}, #{wordRoot.deletedAt})"
        + " ON DUPLICATE KEY UPDATE"
        + " root_code = #{wordRoot.rootCode},"
        + " root_name_cn = #{wordRoot.rootNameCn},"
        + " root_name_en = #{wordRoot.rootNameEn},"
        + " metalake_id = #{wordRoot.metalakeId},"
        + " catalog_id = #{wordRoot.catalogId},"
        + " schema_id = #{wordRoot.schemaId},"
        + " root_comment = #{wordRoot.rootComment},"
        + " audit_info = #{wordRoot.auditInfo},"
        + " deleted_at = #{wordRoot.deletedAt}";
  }

  public String listWordRootPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return "SELECT root_id AS rootId, root_code AS rootCode, root_name_cn AS rootNameCn, root_name_en AS rootNameEn,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, root_comment AS rootComment,"
        + " audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String selectWordRootMetaBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode) {
    return "SELECT root_id AS rootId, root_code AS rootCode, root_name_cn AS rootNameCn, root_name_en AS rootNameEn,"
        + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, root_comment AS rootComment,"
        + " audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND root_code = #{rootCode} AND deleted_at = 0";
  }

  public String selectWordRootIdBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode) {
    return "SELECT root_id FROM "
        + TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND root_code = #{rootCode} AND deleted_at = 0";
  }

  public String updateWordRootMeta(
      @Param("newWordRoot") WordRootPO newWordRootPO,
      @Param("oldWordRoot") WordRootPO oldWordRootPO) {
    return "UPDATE "
        + TABLE_NAME
        + " SET root_code = #{newWordRoot.rootCode},"
        + " root_name_cn = #{newWordRoot.rootNameCn},"
        + " root_name_en = #{newWordRoot.rootNameEn},"
        + " metalake_id = #{newWordRoot.metalakeId},"
        + " catalog_id = #{newWordRoot.catalogId},"
        + " schema_id = #{newWordRoot.schemaId},"
        + " root_comment = #{newWordRoot.rootComment},"
        + " audit_info = #{newWordRoot.auditInfo},"
        + " deleted_at = #{newWordRoot.deletedAt}"
        + " WHERE root_id = #{oldWordRoot.rootId}"
        + " AND root_code = #{oldWordRoot.rootCode}"
        + " AND root_name_cn = #{oldWordRoot.rootNameCn}"
        + " AND root_name_en = #{oldWordRoot.rootNameEn}"
        + " AND metalake_id = #{oldWordRoot.metalakeId}"
        + " AND catalog_id = #{oldWordRoot.catalogId}"
        + " AND schema_id = #{oldWordRoot.schemaId}"
        + " AND ((root_comment = #{oldWordRoot.rootComment}) OR "
        + " (root_comment IS NULL AND #{oldWordRoot.rootComment} IS NULL))"
        + " AND audit_info = #{oldWordRoot.auditInfo}"
        + " AND deleted_at = 0";
  }

  public String softDeleteWordRootMetaBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND root_code = #{rootCode} AND deleted_at = 0";
  }

  public String softDeleteWordRootMetasBySchemaId(@Param("schemaId") Long schemaId) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String softDeleteWordRootMetasByCatalogId(@Param("catalogId") Long catalogId) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE catalog_id = #{catalogId} AND deleted_at = 0";
  }

  public String softDeleteWordRootMetasByMetalakeId(@Param("metalakeId") Long metalakeId) {
    return "UPDATE "
        + TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE metalake_id = #{metalakeId} AND deleted_at = 0";
  }

  public String deleteWordRootMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    return "DELETE FROM "
        + TABLE_NAME
        + " WHERE deleted_at > 0 AND deleted_at < #{legacyTimeline} LIMIT #{limit}";
  }
}
