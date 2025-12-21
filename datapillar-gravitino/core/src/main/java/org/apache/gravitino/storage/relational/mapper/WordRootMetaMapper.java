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
import org.apache.gravitino.storage.relational.po.WordRootPO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.UpdateProvider;

/** WordRoot 元数据映射器 */
public interface WordRootMetaMapper {
  String TABLE_NAME = "wordroot_meta";

  @InsertProvider(type = WordRootMetaSQLProviderFactory.class, method = "insertWordRootMeta")
  void insertWordRootMeta(@Param("wordRoot") WordRootPO wordRootPO);

  @InsertProvider(
      type = WordRootMetaSQLProviderFactory.class,
      method = "insertWordRootMetaOnDuplicateKeyUpdate")
  void insertWordRootMetaOnDuplicateKeyUpdate(@Param("wordRoot") WordRootPO wordRootPO);

  @SelectProvider(type = WordRootMetaSQLProviderFactory.class, method = "listWordRootPOsBySchemaId")
  List<WordRootPO> listWordRootPOsBySchemaId(@Param("schemaId") Long schemaId);

  @SelectProvider(
      type = WordRootMetaSQLProviderFactory.class,
      method = "selectWordRootMetaBySchemaIdAndRootCode")
  WordRootPO selectWordRootMetaBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode);

  @Select(
      "SELECT root_id FROM "
          + TABLE_NAME
          + " WHERE schema_id = #{schemaId} AND root_code = #{rootCode} AND deleted_at = 0")
  Long selectRootIdBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode);

  @Select(
      "SELECT root_id, root_code, root_name_cn, root_name_en,"
          + " metalake_id, catalog_id, schema_id, root_comment,"
          + " audit_info, deleted_at"
          + " FROM "
          + TABLE_NAME
          + " WHERE root_id = #{rootId} AND deleted_at = 0")
  WordRootPO selectWordRootMetaByRootId(@Param("rootId") Long rootId);

  @Update(
      "UPDATE "
          + TABLE_NAME
          + " SET deleted_at = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000"
          + " WHERE schema_id = #{schemaId} AND root_code = #{rootCode} AND deleted_at = 0")
  Integer softDeleteWordRootMetaBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode);

  @Update(
      "UPDATE "
          + TABLE_NAME
          + " SET deleted_at = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000"
          + " WHERE catalog_id = #{catalogId} AND deleted_at = 0")
  Integer softDeleteWordRootMetasByCatalogId(@Param("catalogId") Long catalogId);

  @Update(
      "UPDATE "
          + TABLE_NAME
          + " SET deleted_at = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000"
          + " WHERE metalake_id = #{metalakeId} AND deleted_at = 0")
  Integer softDeleteWordRootMetasByMetalakeId(@Param("metalakeId") Long metalakeId);

  @Update(
      "UPDATE "
          + TABLE_NAME
          + " SET deleted_at = UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000"
          + " WHERE schema_id = #{schemaId} AND deleted_at = 0")
  Integer softDeleteWordRootMetasBySchemaId(@Param("schemaId") Long schemaId);

  @Delete(
      "DELETE FROM "
          + TABLE_NAME
          + " WHERE deleted_at > 0 AND deleted_at < #{legacyTimeline} LIMIT #{limit}")
  Integer deleteWordRootMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit);

  @UpdateProvider(type = WordRootMetaSQLProviderFactory.class, method = "updateWordRootMeta")
  Integer updateWordRootMeta(
      @Param("newWordRoot") WordRootPO newWordRootPO,
      @Param("oldWordRoot") WordRootPO oldWordRootPO);
}
