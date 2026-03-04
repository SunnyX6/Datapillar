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
import org.apache.gravitino.storage.relational.mapper.provider.TenantSqlSupport;
import org.apache.gravitino.storage.relational.po.ModelPO;
import org.apache.ibatis.annotations.Param;

public class ModelMetaBaseSQLProvider {

  public String insertModelMeta(@Param("modelMeta") ModelPO modelPO) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "INSERT INTO "
        + ModelMetaMapper.TABLE_NAME
        + " (model_id, model_name, metalake_id, catalog_id, schema_id,"
        + " model_comment, model_properties, model_latest_version, audit_info, deleted_at, "
        + TenantSqlSupport.tenantColumn()
        + ")"
        + " VALUES (#{modelMeta.modelId}, #{modelMeta.modelName}, #{modelMeta.metalakeId},"
        + " #{modelMeta.catalogId}, #{modelMeta.schemaId}, #{modelMeta.modelComment},"
        + " #{modelMeta.modelProperties}, #{modelMeta.modelLatestVersion}, #{modelMeta.auditInfo},"
        + " #{modelMeta.deletedAt}, "
        + tenantId
        + ")";
  }

  public String insertModelMetaOnDuplicateKeyUpdate(@Param("modelMeta") ModelPO modelPO) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "INSERT INTO "
        + ModelMetaMapper.TABLE_NAME
        + " (model_id, model_name, metalake_id, catalog_id, schema_id,"
        + " model_comment, model_properties, model_latest_version, audit_info, deleted_at, "
        + TenantSqlSupport.tenantColumn()
        + ")"
        + " VALUES (#{modelMeta.modelId}, #{modelMeta.modelName}, #{modelMeta.metalakeId},"
        + " #{modelMeta.catalogId}, #{modelMeta.schemaId}, #{modelMeta.modelComment},"
        + " #{modelMeta.modelProperties}, #{modelMeta.modelLatestVersion}, #{modelMeta.auditInfo},"
        + " #{modelMeta.deletedAt}, "
        + tenantId
        + ")"
        + " ON DUPLICATE KEY UPDATE"
        + " model_name = #{modelMeta.modelName},"
        + " metalake_id = #{modelMeta.metalakeId},"
        + " catalog_id = #{modelMeta.catalogId},"
        + " schema_id = #{modelMeta.schemaId},"
        + " model_comment = #{modelMeta.modelComment},"
        + " model_properties = #{modelMeta.modelProperties},"
        + " model_latest_version = #{modelMeta.modelLatestVersion},"
        + " audit_info = #{modelMeta.auditInfo},"
        + " deleted_at = #{modelMeta.deletedAt}";
  }

  public String listModelPOsBySchemaId(@Param("schemaId") Long schemaId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT model_id AS modelId, model_name AS modelName, metalake_id AS metalakeId,"
        + " catalog_id AS catalogId, schema_id AS schemaId, model_comment AS modelComment,"
        + " model_properties AS modelProperties, model_latest_version AS"
        + " modelLatestVersion, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + ModelMetaMapper.TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String listModelPOsByModelIds(List<Long> modelIds) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "<script>"
        + " SELECT model_id AS modelId, model_name AS modelName, metalake_id AS metalakeId,"
        + " catalog_id AS catalogId, schema_id AS schemaId, model_comment AS modelComment,"
        + " model_properties AS modelProperties, model_latest_version AS"
        + " modelLatestVersion, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + ModelMetaMapper.TABLE_NAME
        + " WHERE deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + " AND model_id in ("
        + "<foreach collection='modelIds' item='modelId' separator=','>"
        + "#{modelId}"
        + "</foreach>"
        + ") "
        + "</script>";
  }

  public String selectModelMetaBySchemaIdAndModelName(
      @Param("schemaId") Long schemaId, @Param("modelName") String modelName) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT model_id AS modelId, model_name AS modelName, metalake_id AS metalakeId,"
        + " catalog_id AS catalogId, schema_id AS schemaId, model_comment AS modelComment,"
        + " model_properties AS modelProperties, model_latest_version AS"
        + " modelLatestVersion, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + ModelMetaMapper.TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND model_name = #{modelName} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String selectModelIdBySchemaIdAndModelName(
      @Param("schemaId") Long schemaId, @Param("modelName") String modelName) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT model_id"
        + " FROM "
        + ModelMetaMapper.TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND model_name = #{modelName} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String selectModelMetaByModelId(@Param("modelId") Long modelId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "SELECT model_id AS modelId, model_name AS modelName, metalake_id AS metalakeId,"
        + " catalog_id AS catalogId, schema_id AS schemaId, model_comment AS modelComment,"
        + " model_properties AS modelProperties, model_latest_version AS "
        + " modelLatestVersion, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + ModelMetaMapper.TABLE_NAME
        + " WHERE model_id = #{modelId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String softDeleteModelMetaBySchemaIdAndModelName(
      @Param("schemaId") Long schemaId, @Param("modelName") String modelName) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + ModelMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND model_name = #{modelName} AND deleted_at = 0"
        + " AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String softDeleteModelMetasByCatalogId(@Param("catalogId") Long catalogId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + ModelMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE catalog_id = #{catalogId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String softDeleteModelMetasByMetalakeId(@Param("metalakeId") Long metalakeId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + ModelMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE metalake_id = #{metalakeId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String softDeleteModelMetasBySchemaId(@Param("schemaId") Long schemaId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + ModelMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String deleteModelMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "DELETE FROM "
        + ModelMetaMapper.TABLE_NAME
        + " WHERE deleted_at > 0 AND deleted_at < #{legacyTimeline} AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId)
        + " LIMIT #{limit}";
  }

  public String updateModelLatestVersion(@Param("modelId") Long modelId) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + ModelMetaMapper.TABLE_NAME
        + " SET model_latest_version = model_latest_version + 1"
        + " WHERE model_id = #{modelId} AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }

  public String updateModelMeta(
      @Param("newModelMeta") ModelPO newModelPO, @Param("oldModelMeta") ModelPO oldModelPO) {
    long tenantId = TenantSqlSupport.requireTenantId();
    return "UPDATE "
        + ModelMetaMapper.TABLE_NAME
        + " SET model_name = #{newModelMeta.modelName},"
        + " metalake_id = #{newModelMeta.metalakeId},"
        + " catalog_id = #{newModelMeta.catalogId},"
        + " schema_id = #{newModelMeta.schemaId},"
        + " model_comment = #{newModelMeta.modelComment},"
        + " model_properties = #{newModelMeta.modelProperties},"
        + " model_latest_version = #{newModelMeta.modelLatestVersion},"
        + " audit_info = #{newModelMeta.auditInfo},"
        + " deleted_at = #{newModelMeta.deletedAt}"
        + " WHERE model_id = #{oldModelMeta.modelId}"
        + " AND model_name = #{oldModelMeta.modelName}"
        + " AND metalake_id = #{oldModelMeta.metalakeId}"
        + " AND catalog_id = #{oldModelMeta.catalogId}"
        + " AND schema_id = #{oldModelMeta.schemaId}"
        + " AND model_comment = #{oldModelMeta.modelComment}"
        + " AND model_properties = #{oldModelMeta.modelProperties}"
        + " AND model_latest_version = #{oldModelMeta.modelLatestVersion}"
        + " AND audit_info = #{oldModelMeta.auditInfo}"
        + " AND deleted_at = 0 AND "
        + TenantSqlSupport.tenantPredicate(null, tenantId);
  }
}
