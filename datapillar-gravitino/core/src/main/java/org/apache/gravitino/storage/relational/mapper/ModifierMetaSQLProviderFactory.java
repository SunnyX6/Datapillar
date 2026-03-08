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
import java.util.List;
import java.util.Map;
import org.apache.gravitino.storage.relational.JDBCBackend.JDBCBackendType;
import org.apache.gravitino.storage.relational.mapper.provider.base.ModifierMetaBaseSQLProvider;
import org.apache.gravitino.storage.relational.mapper.provider.postgresql.ModifierMetaPostgreSQLProvider;
import org.apache.gravitino.storage.relational.po.ModifierPO;
import org.apache.gravitino.storage.relational.session.SqlSessionFactoryHelper;
import org.apache.ibatis.annotations.Param;

public class ModifierMetaSQLProviderFactory {
  private static final Map<JDBCBackendType, ModifierMetaBaseSQLProvider>
      MODIFIER_META_SQL_PROVIDER_MAP =
          ImmutableMap.of(
              JDBCBackendType.MYSQL, new ModifierMetaMySQLProvider(),
              JDBCBackendType.H2, new ModifierMetaH2Provider(),
              JDBCBackendType.POSTGRESQL, new ModifierMetaPostgreSQLProvider());

  public static ModifierMetaBaseSQLProvider getProvider() {
    String databaseId =
        SqlSessionFactoryHelper.getInstance()
            .getSqlSessionFactory()
            .getConfiguration()
            .getDatabaseId();

    JDBCBackendType jdbcBackendType = JDBCBackendType.fromString(databaseId);
    return MODIFIER_META_SQL_PROVIDER_MAP.get(jdbcBackendType);
  }

  static class ModifierMetaMySQLProvider extends ModifierMetaBaseSQLProvider {}

  static class ModifierMetaH2Provider extends ModifierMetaBaseSQLProvider {}

  public static String insertModifierMeta(@Param("modifier") ModifierPO modifierPO) {
    return getProvider().insertModifierMeta(modifierPO);
  }

  public static String insertModifierMetaOnDuplicateKeyUpdate(
      @Param("modifier") ModifierPO modifierPO) {
    return getProvider().insertModifierMetaOnDuplicateKeyUpdate(modifierPO);
  }

  public static String listModifierPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().listModifierPOsBySchemaId(schemaId);
  }

  public static String listModifierPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit) {
    return getProvider().listModifierPOsBySchemaIdWithPagination(schemaId, offset, limit);
  }

  public static String countModifiersBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().countModifiersBySchemaId(schemaId);
  }

  public static String selectModifierMetaBySchemaIdAndModifierCode(
      @Param("schemaId") Long schemaId, @Param("modifierCode") String modifierCode) {
    return getProvider().selectModifierMetaBySchemaIdAndModifierCode(schemaId, modifierCode);
  }

  public static String listModifierPOsByModifierIds(@Param("modifierIds") List<Long> modifierIds) {
    return getProvider().listModifierPOsByModifierIds(modifierIds);
  }

  public static String updateModifierMeta(
      @Param("newModifier") ModifierPO newModifierPO,
      @Param("oldModifier") ModifierPO oldModifierPO) {
    return getProvider().updateModifierMeta(newModifierPO, oldModifierPO);
  }
}
