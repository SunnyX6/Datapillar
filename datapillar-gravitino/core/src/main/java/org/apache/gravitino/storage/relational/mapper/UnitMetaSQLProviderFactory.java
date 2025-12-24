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

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.apache.gravitino.storage.relational.JDBCBackend.JDBCBackendType;
import org.apache.gravitino.storage.relational.mapper.provider.base.UnitMetaBaseSQLProvider;
import org.apache.gravitino.storage.relational.po.UnitPO;
import org.apache.gravitino.storage.relational.session.SqlSessionFactoryHelper;
import org.apache.ibatis.annotations.Param;

/** Unit 元数据 SQL Provider 工厂 */
public class UnitMetaSQLProviderFactory {

  private static final Map<JDBCBackendType, UnitMetaBaseSQLProvider> UNIT_META_SQL_PROVIDER_MAP =
      ImmutableMap.of(
          JDBCBackendType.MYSQL, new UnitMetaBaseSQLProvider(),
          JDBCBackendType.H2, new UnitMetaBaseSQLProvider(),
          JDBCBackendType.POSTGRESQL, new UnitMetaBaseSQLProvider());

  public static UnitMetaBaseSQLProvider getProvider() {
    String databaseId =
        SqlSessionFactoryHelper.getInstance()
            .getSqlSessionFactory()
            .getConfiguration()
            .getDatabaseId();

    JDBCBackendType jdbcBackendType = JDBCBackendType.fromString(databaseId);
    return UNIT_META_SQL_PROVIDER_MAP.get(jdbcBackendType);
  }

  public static String insertUnitMeta(@Param("unit") UnitPO unitPO) {
    return getProvider().insertUnitMeta(unitPO);
  }

  public static String insertUnitMetaOnDuplicateKeyUpdate(@Param("unit") UnitPO unitPO) {
    return getProvider().insertUnitMetaOnDuplicateKeyUpdate(unitPO);
  }

  public static String listUnitPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().listUnitPOsBySchemaId(schemaId);
  }

  public static String listUnitPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit) {
    return getProvider().listUnitPOsBySchemaIdWithPagination(schemaId, offset, limit);
  }

  public static String countUnitsBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().countUnitsBySchemaId(schemaId);
  }

  public static String selectUnitMetaBySchemaIdAndUnitCode(
      @Param("schemaId") Long schemaId, @Param("unitCode") String unitCode) {
    return getProvider().selectUnitMetaBySchemaIdAndUnitCode(schemaId, unitCode);
  }

  public static String selectUnitIdBySchemaIdAndUnitCode(
      @Param("schemaId") Long schemaId, @Param("unitCode") String unitCode) {
    return getProvider().selectUnitIdBySchemaIdAndUnitCode(schemaId, unitCode);
  }

  public static String updateUnitMeta(
      @Param("newUnit") UnitPO newUnitPO, @Param("oldUnit") UnitPO oldUnitPO) {
    return getProvider().updateUnitMeta(newUnitPO, oldUnitPO);
  }

  public static String softDeleteUnitMetaBySchemaIdAndUnitCode(
      @Param("schemaId") Long schemaId, @Param("unitCode") String unitCode) {
    return getProvider().softDeleteUnitMetaBySchemaIdAndUnitCode(schemaId, unitCode);
  }

  public static String softDeleteUnitMetasBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().softDeleteUnitMetasBySchemaId(schemaId);
  }

  public static String softDeleteUnitMetasByCatalogId(@Param("catalogId") Long catalogId) {
    return getProvider().softDeleteUnitMetasByCatalogId(catalogId);
  }

  public static String softDeleteUnitMetasByMetalakeId(@Param("metalakeId") Long metalakeId) {
    return getProvider().softDeleteUnitMetasByMetalakeId(metalakeId);
  }

  public static String deleteUnitMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    return getProvider().deleteUnitMetasByLegacyTimeline(legacyTimeline, limit);
  }
}
