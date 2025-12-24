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
import org.apache.gravitino.storage.relational.po.UnitPO;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

/** Unit 元数据 Mapper */
public interface UnitMetaMapper {

  String TABLE_NAME = "unit_meta";

  @InsertProvider(type = UnitMetaSQLProviderFactory.class, method = "insertUnitMeta")
  void insertUnitMeta(@Param("unit") UnitPO unitPO);

  @InsertProvider(
      type = UnitMetaSQLProviderFactory.class,
      method = "insertUnitMetaOnDuplicateKeyUpdate")
  void insertUnitMetaOnDuplicateKeyUpdate(@Param("unit") UnitPO unitPO);

  @SelectProvider(type = UnitMetaSQLProviderFactory.class, method = "listUnitPOsBySchemaId")
  List<UnitPO> listUnitPOsBySchemaId(@Param("schemaId") Long schemaId);

  @SelectProvider(
      type = UnitMetaSQLProviderFactory.class,
      method = "listUnitPOsBySchemaIdWithPagination")
  List<UnitPO> listUnitPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit);

  @SelectProvider(type = UnitMetaSQLProviderFactory.class, method = "countUnitsBySchemaId")
  Long countUnitsBySchemaId(@Param("schemaId") Long schemaId);

  @SelectProvider(
      type = UnitMetaSQLProviderFactory.class,
      method = "selectUnitMetaBySchemaIdAndUnitCode")
  UnitPO selectUnitMetaBySchemaIdAndUnitCode(
      @Param("schemaId") Long schemaId, @Param("unitCode") String unitCode);

  @SelectProvider(
      type = UnitMetaSQLProviderFactory.class,
      method = "selectUnitIdBySchemaIdAndUnitCode")
  Long selectUnitIdBySchemaIdAndUnitCode(
      @Param("schemaId") Long schemaId, @Param("unitCode") String unitCode);

  @UpdateProvider(type = UnitMetaSQLProviderFactory.class, method = "updateUnitMeta")
  Integer updateUnitMeta(@Param("newUnit") UnitPO newUnitPO, @Param("oldUnit") UnitPO oldUnitPO);

  @UpdateProvider(
      type = UnitMetaSQLProviderFactory.class,
      method = "softDeleteUnitMetaBySchemaIdAndUnitCode")
  Integer softDeleteUnitMetaBySchemaIdAndUnitCode(
      @Param("schemaId") Long schemaId, @Param("unitCode") String unitCode);

  @UpdateProvider(type = UnitMetaSQLProviderFactory.class, method = "softDeleteUnitMetasBySchemaId")
  Integer softDeleteUnitMetasBySchemaId(@Param("schemaId") Long schemaId);

  @UpdateProvider(
      type = UnitMetaSQLProviderFactory.class,
      method = "softDeleteUnitMetasByCatalogId")
  Integer softDeleteUnitMetasByCatalogId(@Param("catalogId") Long catalogId);

  @UpdateProvider(
      type = UnitMetaSQLProviderFactory.class,
      method = "softDeleteUnitMetasByMetalakeId")
  Integer softDeleteUnitMetasByMetalakeId(@Param("metalakeId") Long metalakeId);

  @DeleteProvider(
      type = UnitMetaSQLProviderFactory.class,
      method = "deleteUnitMetasByLegacyTimeline")
  Integer deleteUnitMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit);
}
