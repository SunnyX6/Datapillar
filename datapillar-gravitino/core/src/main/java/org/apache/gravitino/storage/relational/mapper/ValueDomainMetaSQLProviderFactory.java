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
import org.apache.gravitino.storage.relational.mapper.provider.base.ValueDomainMetaBaseSQLProvider;
import org.apache.gravitino.storage.relational.po.ValueDomainPO;
import org.apache.gravitino.storage.relational.session.SqlSessionFactoryHelper;
import org.apache.ibatis.annotations.Param;

/** ValueDomain 元数据 SQL Provider 工厂 */
public class ValueDomainMetaSQLProviderFactory {

  private static final Map<JDBCBackendType, ValueDomainMetaBaseSQLProvider>
      VALUE_DOMAIN_META_SQL_PROVIDER_MAP =
          ImmutableMap.of(
              JDBCBackendType.MYSQL, new ValueDomainMetaBaseSQLProvider(),
              JDBCBackendType.H2, new ValueDomainMetaBaseSQLProvider(),
              JDBCBackendType.POSTGRESQL, new ValueDomainMetaBaseSQLProvider());

  public static ValueDomainMetaBaseSQLProvider getProvider() {
    String databaseId =
        SqlSessionFactoryHelper.getInstance()
            .getSqlSessionFactory()
            .getConfiguration()
            .getDatabaseId();

    JDBCBackendType jdbcBackendType = JDBCBackendType.fromString(databaseId);
    return VALUE_DOMAIN_META_SQL_PROVIDER_MAP.get(jdbcBackendType);
  }

  public static String insertValueDomainMeta(@Param("domain") ValueDomainPO domainPO) {
    return getProvider().insertValueDomainMeta(domainPO);
  }

  public static String insertValueDomainMetaOnDuplicateKeyUpdate(
      @Param("domain") ValueDomainPO domainPO) {
    return getProvider().insertValueDomainMetaOnDuplicateKeyUpdate(domainPO);
  }

  public static String listValueDomainPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().listValueDomainPOsBySchemaId(schemaId);
  }

  public static String listValueDomainPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit) {
    return getProvider().listValueDomainPOsBySchemaIdWithPagination(schemaId, offset, limit);
  }

  public static String countValueDomainsBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().countValueDomainsBySchemaId(schemaId);
  }

  public static String selectValueDomainMetaBySchemaIdAndDomainCode(
      @Param("schemaId") Long schemaId, @Param("domainCode") String domainCode) {
    return getProvider().selectValueDomainMetaBySchemaIdAndDomainCode(schemaId, domainCode);
  }

  public static String selectValueDomainMetaByDomainId(@Param("domainId") Long domainId) {
    return getProvider().selectValueDomainMetaByDomainId(domainId);
  }

  public static String listValueDomainPOsByDomainIds(
      @Param("domainIds") java.util.List<Long> domainIds) {
    return getProvider().listValueDomainPOsByDomainIds(domainIds);
  }

  public static String updateValueDomainMeta(
      @Param("newDomain") ValueDomainPO newDomainPO,
      @Param("oldDomain") ValueDomainPO oldDomainPO) {
    return getProvider().updateValueDomainMeta(newDomainPO, oldDomainPO);
  }

  public static String softDeleteValueDomainMetaBySchemaIdAndDomainCode(
      @Param("schemaId") Long schemaId, @Param("domainCode") String domainCode) {
    return getProvider().softDeleteValueDomainMetaBySchemaIdAndDomainCode(schemaId, domainCode);
  }

  public static String softDeleteValueDomainMetasBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().softDeleteValueDomainMetasBySchemaId(schemaId);
  }

  public static String softDeleteValueDomainMetasByCatalogId(@Param("catalogId") Long catalogId) {
    return getProvider().softDeleteValueDomainMetasByCatalogId(catalogId);
  }

  public static String softDeleteValueDomainMetasByMetalakeId(
      @Param("metalakeId") Long metalakeId) {
    return getProvider().softDeleteValueDomainMetasByMetalakeId(metalakeId);
  }

  public static String deleteValueDomainMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    return getProvider().deleteValueDomainMetasByLegacyTimeline(legacyTimeline, limit);
  }
}
