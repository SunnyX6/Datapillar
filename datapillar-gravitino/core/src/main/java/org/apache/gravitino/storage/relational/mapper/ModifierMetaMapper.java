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
import org.apache.gravitino.storage.relational.po.ModifierPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;

/** Modifier metadata mapper */
public interface ModifierMetaMapper {
  String TABLE_NAME = "modifier_meta";

  @InsertProvider(type = ModifierMetaSQLProviderFactory.class, method = "insertModifierMeta")
  void insertModifierMeta(@Param("modifier") ModifierPO modifierPO);

  @InsertProvider(
      type = ModifierMetaSQLProviderFactory.class,
      method = "insertModifierMetaOnDuplicateKeyUpdate")
  void insertModifierMetaOnDuplicateKeyUpdate(@Param("modifier") ModifierPO modifierPO);

  @SelectProvider(type = ModifierMetaSQLProviderFactory.class, method = "listModifierPOsBySchemaId")
  List<ModifierPO> listModifierPOsBySchemaId(@Param("schemaId") Long schemaId);

  @SelectProvider(
      type = ModifierMetaSQLProviderFactory.class,
      method = "listModifierPOsBySchemaIdWithPagination")
  List<ModifierPO> listModifierPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit);

  @SelectProvider(type = ModifierMetaSQLProviderFactory.class, method = "countModifiersBySchemaId")
  long countModifiersBySchemaId(@Param("schemaId") Long schemaId);

  @SelectProvider(
      type = ModifierMetaSQLProviderFactory.class,
      method = "selectModifierMetaBySchemaIdAndModifierCode")
  ModifierPO selectModifierMetaBySchemaIdAndModifierCode(
      @Param("schemaId") Long schemaId, @Param("modifierCode") String modifierCode);

  @Select(
      "SELECT modifier_id FROM "
          + TABLE_NAME
          + " WHERE schema_id = #{schemaId} AND modifier_code = #{modifierCode} AND deleted_at = 0")
  Long selectModifierIdBySchemaIdAndModifierCode(
      @Param("schemaId") Long schemaId, @Param("modifierCode") String modifierCode);

  @Select(
      "SELECT modifier_id AS modifierId, modifier_name AS modifierName, modifier_code AS modifierCode,"
          + " metalake_id AS metalakeId, catalog_id AS catalogId, schema_id AS schemaId, modifier_comment AS modifierComment,"
          + " modifier_type AS modifierType, audit_info AS auditInfo, deleted_at AS deletedAt"
          + " FROM "
          + TABLE_NAME
          + " WHERE modifier_id = #{modifierId} AND deleted_at = 0")
  ModifierPO selectModifierMetaByModifierId(@Param("modifierId") Long modifierId);

  @SelectProvider(
      type = ModifierMetaSQLProviderFactory.class,
      method = "listModifierPOsByModifierIds")
  List<ModifierPO> listModifierPOsByModifierIds(@Param("modifierIds") List<Long> modifierIds);

  @Update(
      "UPDATE "
          + TABLE_NAME
          + " SET deleted_at = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000"
          + " WHERE schema_id = #{schemaId} AND modifier_code = #{modifierCode} AND deleted_at = 0")
  Integer softDeleteModifierMetaBySchemaIdAndModifierCode(
      @Param("schemaId") Long schemaId, @Param("modifierCode") String modifierCode);

  @Update(
      "UPDATE "
          + TABLE_NAME
          + " SET deleted_at = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000"
          + " WHERE catalog_id = #{catalogId} AND deleted_at = 0")
  Integer softDeleteModifierMetasByCatalogId(@Param("catalogId") Long catalogId);

  @Update(
      "UPDATE "
          + TABLE_NAME
          + " SET deleted_at = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000"
          + " WHERE metalake_id = #{metalakeId} AND deleted_at = 0")
  Integer softDeleteModifierMetasByMetalakeId(@Param("metalakeId") Long metalakeId);

  @Update(
      "UPDATE "
          + TABLE_NAME
          + " SET deleted_at = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000"
          + " WHERE schema_id = #{schemaId} AND deleted_at = 0")
  Integer softDeleteModifierMetasBySchemaId(@Param("schemaId") Long schemaId);

  @Delete(
      "DELETE FROM "
          + TABLE_NAME
          + " WHERE deleted_at > 0 AND deleted_at < #{legacyTimeline} LIMIT #{limit}")
  Integer deleteModifierMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit);

  @UpdateProvider(type = ModifierMetaSQLProviderFactory.class, method = "updateModifierMeta")
  Integer updateModifierMeta(
      @Param("newModifier") ModifierPO newModifierPO,
      @Param("oldModifier") ModifierPO oldModifierPO);
}
