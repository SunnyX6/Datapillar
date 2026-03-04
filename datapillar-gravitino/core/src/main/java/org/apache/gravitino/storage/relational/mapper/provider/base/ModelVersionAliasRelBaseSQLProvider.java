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

import java.util.List;
import org.apache.gravitino.storage.relational.mapper.ModelMetaMapper;
import org.apache.gravitino.storage.relational.mapper.ModelVersionAliasRelMapper;
import org.apache.gravitino.storage.relational.mapper.provider.TenantSqlSupport;
import org.apache.gravitino.storage.relational.po.ModelVersionAliasRelPO;
import org.apache.ibatis.annotations.Param;

public class ModelVersionAliasRelBaseSQLProvider {

  public String insertModelVersionAliasRels(
      @Param("modelVersionAliasRel") List<ModelVersionAliasRelPO> modelVersionAliasRelPOs) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "<script>"
        + "INSERT INTO "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " (model_id, model_version, model_version_alias, deleted_at, "
        + TenantSqlSupport.tenantColumn()
        + ")"
        + " VALUES "
        + " <foreach collection='modelVersionAliasRel' item='item' separator=','>"
        + " (#{item.modelId},"
        + " (SELECT model_latest_version FROM "
        + ModelMetaMapper.TABLE_NAME
        + " WHERE model_id = #{item.modelId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + "),"
        + " #{item.modelVersionAlias},"
        + " #{item.deletedAt},"
        + " "
        + tenantId
        + ")"
        + " </foreach>"
        + "</script>";
  }

  public String selectModelVersionAliasRelsByModelId(@Param("modelId") Long modelId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT model_id AS modelId, model_version AS modelVersion,"
        + " model_version_alias AS modelVersionAlias, deleted_at AS deletedAt"
        + " FROM "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " WHERE model_id = #{modelId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String selectModelVersionAliasRelsByModelIdAndVersion(
      @Param("modelId") Long modelId, @Param("modelVersion") Integer modelVersion) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT model_id AS modelId, model_version AS modelVersion,"
        + " model_version_alias AS modelVersionAlias, deleted_at AS deletedAt"
        + " FROM "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " WHERE model_id = #{modelId} AND model_version = #{modelVersion} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String selectModelVersionAliasRelsByModelIdAndAlias(
      @Param("modelId") Long modelId, @Param("alias") String alias) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT model_id AS modelId, model_version AS modelVersion,"
        + " model_version_alias AS modelVersionAlias, deleted_at AS deletedAt"
        + " FROM "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " WHERE model_id = #{modelId} AND model_version = ("
        + " SELECT model_version FROM "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " WHERE model_id = #{modelId} AND model_version_alias = #{alias} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + ")"
        + " AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String softDeleteModelVersionAliasRelsBySchemaIdAndModelName(
      @Param("schemaId") Long schemaId, @Param("modelName") String modelName) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " mvar SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE mvar.model_id = ("
        + " SELECT mm.model_id FROM "
        + ModelMetaMapper.TABLE_NAME
        + " mm WHERE mm.schema_id = #{schemaId} AND mm.model_name = #{modelName}"
        + " AND mm.deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate("mm", tenantId)
        + ") AND mvar.deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate("mvar", tenantId);
  }

  public String softDeleteModelVersionAliasRelsByModelIdAndVersion(
      @Param("modelId") Long modelId, @Param("modelVersion") Integer modelVersion) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE model_id = #{modelId} AND model_version = #{modelVersion} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String softDeleteModelVersionAliasRelsByModelIdAndAlias(
      @Param("modelId") Long modelId, @Param("alias") String alias) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " mvar JOIN ("
        + " SELECT model_version FROM "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " WHERE model_id = #{modelId} AND model_version_alias = #{alias} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + ")"
        + " subquery ON mvar.model_version = subquery.model_version"
        + " SET mvar.deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE mvar.model_id = #{modelId} AND mvar.deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate("mvar", tenantId);
  }

  public String softDeleteModelVersionAliasRelsBySchemaId(@Param("schemaId") Long schemaId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE model_id IN ("
        + " SELECT model_id FROM "
        + ModelMetaMapper.TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + ") AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String softDeleteModelVersionAliasRelsByCatalogId(@Param("catalogId") Long catalogId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE model_id IN ("
        + " SELECT model_id FROM "
        + ModelMetaMapper.TABLE_NAME
        + " WHERE catalog_id = #{catalogId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + ") AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String softDeleteModelVersionAliasRelsByMetalakeId(@Param("metalakeId") Long metalakeId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE model_id IN ("
        + " SELECT model_id FROM "
        + ModelMetaMapper.TABLE_NAME
        + " WHERE metalake_id = #{metalakeId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + ") AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String deleteModelVersionAliasRelsByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "DELETE FROM "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " WHERE deleted_at > 0 AND deleted_at < #{legacyTimeline} AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + " LIMIT #{limit}";
  }

  public String updateModelVersionAliasRel(
      @Param("modelVersionAliasRel") List<ModelVersionAliasRelPO> modelVersionAliasRelPOs) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "<script>"
        + "INSERT INTO "
        + ModelVersionAliasRelMapper.TABLE_NAME
        + " (model_id, model_version, model_version_alias, deleted_at, "
        + TenantSqlSupport.tenantColumn()
        + ")"
        + " VALUES "
        + " <foreach collection='modelVersionAliasRel' item='item' separator=','>"
        + " (#{item.modelId},"
        + " #{item.modelVersion},"
        + " #{item.modelVersionAlias},"
        + " #{item.deletedAt},"
        + " "
        + tenantId
        + ")"
        + " </foreach>"
        + "</script>";
  }
}
