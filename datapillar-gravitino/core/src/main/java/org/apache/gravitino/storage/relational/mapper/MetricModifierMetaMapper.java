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
package org.apache.gravitino.storage.relational.mapper;

import java.util.List;
import org.apache.gravitino.storage.relational.po.MetricModifierPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;

/** MetricModifier 元数据映射器 */
public interface MetricModifierMetaMapper {
  String TABLE_NAME = "metric_modifier_meta";

  @Insert(
      "INSERT INTO "
          + TABLE_NAME
          + " (modifier_id, modifier_name, modifier_code, modifier_type,"
          + " metalake_id, catalog_id, schema_id, modifier_comment,"
          + " audit_info, deleted_at)"
          + " VALUES (#{metricModifier.modifierId}, #{metricModifier.modifierName},"
          + " #{metricModifier.modifierCode}, #{metricModifier.modifierType},"
          + " #{metricModifier.metalakeId}, #{metricModifier.catalogId},"
          + " #{metricModifier.schemaId}, #{metricModifier.modifierComment},"
          + " #{metricModifier.auditInfo}, #{metricModifier.deletedAt})")
  void insertMetricModifierMeta(@Param("metricModifier") MetricModifierPO metricModifierPO);

  @Insert(
      "INSERT INTO "
          + TABLE_NAME
          + " (modifier_id, modifier_name, modifier_code, modifier_type,"
          + " metalake_id, catalog_id, schema_id, modifier_comment,"
          + " audit_info, deleted_at)"
          + " VALUES (#{metricModifier.modifierId}, #{metricModifier.modifierName},"
          + " #{metricModifier.modifierCode}, #{metricModifier.modifierType},"
          + " #{metricModifier.metalakeId}, #{metricModifier.catalogId},"
          + " #{metricModifier.schemaId}, #{metricModifier.modifierComment},"
          + " #{metricModifier.auditInfo}, #{metricModifier.deletedAt})"
          + " ON DUPLICATE KEY UPDATE"
          + " modifier_name = #{metricModifier.modifierName},"
          + " modifier_code = #{metricModifier.modifierCode},"
          + " modifier_type = #{metricModifier.modifierType},"
          + " metalake_id = #{metricModifier.metalakeId},"
          + " catalog_id = #{metricModifier.catalogId},"
          + " schema_id = #{metricModifier.schemaId},"
          + " modifier_comment = #{metricModifier.modifierComment},"
          + " audit_info = #{metricModifier.auditInfo},"
          + " deleted_at = #{metricModifier.deletedAt}")
  void insertMetricModifierMetaOnDuplicateKeyUpdate(
      @Param("metricModifier") MetricModifierPO metricModifierPO);

  @SelectProvider(
      type = MetricModifierMetaSQLProviderFactory.class,
      method = "listMetricModifierPOsBySchemaId")
  List<MetricModifierPO> listMetricModifierPOsBySchemaId(@Param("schemaId") Long schemaId);

  @SelectProvider(
      type = MetricModifierMetaSQLProviderFactory.class,
      method = "listMetricModifierPOsBySchemaIdWithPagination")
  List<MetricModifierPO> listMetricModifierPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit);

  @SelectProvider(
      type = MetricModifierMetaSQLProviderFactory.class,
      method = "countMetricModifiersBySchemaId")
  long countMetricModifiersBySchemaId(@Param("schemaId") Long schemaId);

  @SelectProvider(
      type = MetricModifierMetaSQLProviderFactory.class,
      method = "selectMetricModifierMetaBySchemaIdAndModifierCode")
  MetricModifierPO selectMetricModifierMetaBySchemaIdAndModifierCode(
      @Param("schemaId") Long schemaId, @Param("modifierCode") String modifierCode);

  @Select(
      "SELECT modifier_id FROM "
          + TABLE_NAME
          + " WHERE schema_id = #{schemaId} AND modifier_code = #{modifierCode} AND deleted_at = 0")
  Long selectModifierIdBySchemaIdAndModifierCode(
      @Param("schemaId") Long schemaId, @Param("modifierCode") String modifierCode);

  @Select(
      "SELECT modifier_id, modifier_name, modifier_code, modifier_type,"
          + " metalake_id, catalog_id, schema_id, modifier_comment,"
          + " audit_info, deleted_at"
          + " FROM "
          + TABLE_NAME
          + " WHERE modifier_id = #{modifierId} AND deleted_at = 0")
  MetricModifierPO selectMetricModifierMetaByModifierId(@Param("modifierId") Long modifierId);

  @Update(
      "UPDATE "
          + TABLE_NAME
          + " SET deleted_at = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000"
          + " WHERE schema_id = #{schemaId} AND modifier_code = #{modifierCode} AND deleted_at = 0")
  Integer softDeleteMetricModifierMetaBySchemaIdAndModifierCode(
      @Param("schemaId") Long schemaId, @Param("modifierCode") String modifierCode);

  @Update(
      "UPDATE "
          + TABLE_NAME
          + " SET deleted_at = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000"
          + " WHERE catalog_id = #{catalogId} AND deleted_at = 0")
  Integer softDeleteMetricModifierMetasByCatalogId(@Param("catalogId") Long catalogId);

  @Update(
      "UPDATE "
          + TABLE_NAME
          + " SET deleted_at = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000"
          + " WHERE metalake_id = #{metalakeId} AND deleted_at = 0")
  Integer softDeleteMetricModifierMetasByMetalakeId(@Param("metalakeId") Long metalakeId);

  @Update(
      "UPDATE "
          + TABLE_NAME
          + " SET deleted_at = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000"
          + " WHERE schema_id = #{schemaId} AND deleted_at = 0")
  Integer softDeleteMetricModifierMetasBySchemaId(@Param("schemaId") Long schemaId);

  @Delete(
      "DELETE FROM "
          + TABLE_NAME
          + " WHERE deleted_at > 0 AND deleted_at < #{legacyTimeline} LIMIT #{limit}")
  Integer deleteMetricModifierMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit);

  @UpdateProvider(
      type = MetricModifierMetaSQLProviderFactory.class,
      method = "updateMetricModifierMeta")
  Integer updateMetricModifierMeta(
      @Param("newMetricModifier") MetricModifierPO newMetricModifierPO,
      @Param("oldMetricModifier") MetricModifierPO oldMetricModifierPO);
}
