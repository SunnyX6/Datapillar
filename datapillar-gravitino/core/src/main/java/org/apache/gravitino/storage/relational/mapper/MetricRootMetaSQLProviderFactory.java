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
import org.apache.gravitino.storage.relational.mapper.provider.base.MetricRootMetaBaseSQLProvider;
import org.apache.gravitino.storage.relational.mapper.provider.postgresql.MetricRootMetaPostgreSQLProvider;
import org.apache.gravitino.storage.relational.po.MetricRootPO;
import org.apache.gravitino.storage.relational.session.SqlSessionFactoryHelper;
import org.apache.ibatis.annotations.Param;

public class MetricRootMetaSQLProviderFactory {

  static class MetricRootMetaMySQLProvider extends MetricRootMetaBaseSQLProvider {}

  static class MetricRootMetaH2Provider extends MetricRootMetaBaseSQLProvider {}

  private static final Map<JDBCBackendType, MetricRootMetaBaseSQLProvider>
      METRIC_ROOT_META_SQL_PROVIDER_MAP =
          ImmutableMap.of(
              JDBCBackendType.MYSQL, new MetricRootMetaMySQLProvider(),
              JDBCBackendType.H2, new MetricRootMetaH2Provider(),
              JDBCBackendType.POSTGRESQL, new MetricRootMetaPostgreSQLProvider());

  public static MetricRootMetaBaseSQLProvider getProvider() {
    String databaseId =
        SqlSessionFactoryHelper.getInstance()
            .getSqlSessionFactory()
            .getConfiguration()
            .getDatabaseId();

    JDBCBackendType jdbcBackendType = JDBCBackendType.fromString(databaseId);
    return METRIC_ROOT_META_SQL_PROVIDER_MAP.get(jdbcBackendType);
  }

  public static String insertMetricRootMeta(@Param("metricRoot") MetricRootPO metricRootPO) {
    return getProvider().insertMetricRootMeta(metricRootPO);
  }

  public static String insertMetricRootMetaOnDuplicateKeyUpdate(
      @Param("metricRoot") MetricRootPO metricRootPO) {
    return getProvider().insertMetricRootMetaOnDuplicateKeyUpdate(metricRootPO);
  }

  public static String listMetricRootPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().listMetricRootPOsBySchemaId(schemaId);
  }

  public static String selectMetricRootMetaBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode) {
    return getProvider().selectMetricRootMetaBySchemaIdAndRootCode(schemaId, rootCode);
  }

  public static String selectMetricRootIdBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode) {
    return getProvider().selectMetricRootIdBySchemaIdAndRootCode(schemaId, rootCode);
  }

  public static String updateMetricRootMeta(
      @Param("newMetricRoot") MetricRootPO newMetricRootPO,
      @Param("oldMetricRoot") MetricRootPO oldMetricRootPO) {
    return getProvider().updateMetricRootMeta(newMetricRootPO, oldMetricRootPO);
  }

  public static String softDeleteMetricRootMetaBySchemaIdAndRootCode(
      @Param("schemaId") Long schemaId, @Param("rootCode") String rootCode) {
    return getProvider().softDeleteMetricRootMetaBySchemaIdAndRootCode(schemaId, rootCode);
  }

  public static String softDeleteMetricRootMetasBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().softDeleteMetricRootMetasBySchemaId(schemaId);
  }

  public static String softDeleteMetricRootMetasByCatalogId(@Param("catalogId") Long catalogId) {
    return getProvider().softDeleteMetricRootMetasByCatalogId(catalogId);
  }

  public static String softDeleteMetricRootMetasByMetalakeId(@Param("metalakeId") Long metalakeId) {
    return getProvider().softDeleteMetricRootMetasByMetalakeId(metalakeId);
  }

  public static String deleteMetricRootMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    return getProvider().deleteMetricRootMetasByLegacyTimeline(legacyTimeline, limit);
  }
}
