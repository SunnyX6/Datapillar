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
import org.apache.gravitino.storage.relational.mapper.provider.base.WordRootMetaBaseSQLProvider;
import org.apache.gravitino.storage.relational.mapper.provider.postgresql.WordRootMetaPostgreSQLProvider;
import org.apache.gravitino.storage.relational.po.WordRootPO;
import org.apache.gravitino.storage.relational.session.SqlSessionFactoryHelper;
import org.apache.ibatis.annotations.Param;

public class WordRootMetaSQLProviderFactory {

  static class WordRootMetaMySQLProvider extends WordRootMetaBaseSQLProvider {}

  static class WordRootMetaH2Provider extends WordRootMetaBaseSQLProvider {}

  private static final Map<JDBCBackendType, WordRootMetaBaseSQLProvider>
      WORD_ROOT_META_SQL_PROVIDER_MAP =
          ImmutableMap.of(
              JDBCBackendType.MYSQL, new WordRootMetaMySQLProvider(),
              JDBCBackendType.H2, new WordRootMetaH2Provider(),
              JDBCBackendType.POSTGRESQL, new WordRootMetaPostgreSQLProvider());

  public static WordRootMetaBaseSQLProvider getProvider() {
    String databaseId =
        SqlSessionFactoryHelper.getInstance()
            .getSqlSessionFactory()
            .getConfiguration()
            .getDatabaseId();

    JDBCBackendType jdbcBackendType = JDBCBackendType.fromString(databaseId);
    return WORD_ROOT_META_SQL_PROVIDER_MAP.get(jdbcBackendType);
  }

  public static String insertWordRootMeta(@Param("wordRoot") WordRootPO wordRootPO) {
    return getProvider().insertWordRootMeta(wordRootPO);
  }

  public static String insertWordRootMetaOnDuplicateKeyUpdate(
      @Param("wordRoot") WordRootPO wordRootPO) {
    return getProvider().insertWordRootMetaOnDuplicateKeyUpdate(wordRootPO);
  }

  public static String listWordRootPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().listWordRootPOsBySchemaId(schemaId);
  }

  public static String selectWordRootMetaBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode) {
    return getProvider().selectWordRootMetaBySchemaIdAndRootCode(schemaId, rootCode);
  }

  public static String selectWordRootIdBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode) {
    return getProvider().selectWordRootIdBySchemaIdAndRootCode(schemaId, rootCode);
  }

  public static String updateWordRootMeta(
      @Param("newWordRoot") WordRootPO newWordRootPO,
      @Param("oldWordRoot") WordRootPO oldWordRootPO) {
    return getProvider().updateWordRootMeta(newWordRootPO, oldWordRootPO);
  }

  public static String softDeleteWordRootMetaBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode) {
    return getProvider().softDeleteWordRootMetaBySchemaIdAndRootCode(schemaId, rootCode);
  }

  public static String softDeleteWordRootMetasBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().softDeleteWordRootMetasBySchemaId(schemaId);
  }

  public static String softDeleteWordRootMetasByCatalogId(@Param("catalogId") Long catalogId) {
    return getProvider().softDeleteWordRootMetasByCatalogId(catalogId);
  }

  public static String softDeleteWordRootMetasByMetalakeId(@Param("metalakeId") Long metalakeId) {
    return getProvider().softDeleteWordRootMetasByMetalakeId(metalakeId);
  }

  public static String deleteWordRootMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    return getProvider().deleteWordRootMetasByLegacyTimeline(legacyTimeline, limit);
  }
}
