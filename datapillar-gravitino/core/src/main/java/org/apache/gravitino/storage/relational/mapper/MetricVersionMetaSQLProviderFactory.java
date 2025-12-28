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
import org.apache.gravitino.storage.relational.mapper.provider.base.MetricVersionMetaBaseSQLProvider;
import org.apache.gravitino.storage.relational.po.MetricVersionPO;
import org.apache.gravitino.storage.relational.session.SqlSessionFactoryHelper;
import org.apache.ibatis.annotations.Param;

public class MetricVersionMetaSQLProviderFactory {

  static class MetricVersionMetaMySQLProvider extends MetricVersionMetaBaseSQLProvider {}

  static class MetricVersionMetaH2Provider extends MetricVersionMetaBaseSQLProvider {}

  private static final Map<JDBCBackendType, MetricVersionMetaBaseSQLProvider>
      METRIC_VERSION_META_SQL_PROVIDER_MAP =
          ImmutableMap.of(
              JDBCBackendType.MYSQL, new MetricVersionMetaMySQLProvider(),
              JDBCBackendType.H2, new MetricVersionMetaH2Provider());

  public static MetricVersionMetaBaseSQLProvider getProvider() {
    String databaseId =
        SqlSessionFactoryHelper.getInstance()
            .getSqlSessionFactory()
            .getConfiguration()
            .getDatabaseId();

    JDBCBackendType jdbcBackendType = JDBCBackendType.fromString(databaseId);
    return METRIC_VERSION_META_SQL_PROVIDER_MAP.get(jdbcBackendType);
  }

  public static String insertMetricVersionMeta(
      @Param("metricVersionMeta") MetricVersionPO metricVersionPO) {
    return getProvider().insertMetricVersionMeta(metricVersionPO);
  }

  public static String listMetricVersionMetasByMetricId(@Param("metricId") Long metricId) {
    return getProvider().listMetricVersionMetasByMetricId(metricId);
  }

  public static String selectMetricVersionMetaById(@Param("id") Long id) {
    return getProvider().selectMetricVersionMetaById(id);
  }

  public static String selectMetricVersionMetaByMetricIdAndVersion(
      @Param("metricId") Long metricId, @Param("version") Integer version) {
    return getProvider().selectMetricVersionMetaByMetricIdAndVersion(metricId, version);
  }

  public static String softDeleteMetricVersionsBySchemaIdAndMetricCode(
      @Param("schemaId") Long schemaId, @Param("metricCode") String metricCode) {
    return getProvider().softDeleteMetricVersionsBySchemaIdAndMetricCode(schemaId, metricCode);
  }

  public static String softDeleteMetricVersionMetaById(@Param("id") Long id) {
    return getProvider().softDeleteMetricVersionMetaById(id);
  }

  public static String softDeleteMetricVersionMetasBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().softDeleteMetricVersionMetasBySchemaId(schemaId);
  }

  public static String softDeleteMetricVersionMetasByCatalogId(@Param("catalogId") Long catalogId) {
    return getProvider().softDeleteMetricVersionMetasByCatalogId(catalogId);
  }

  public static String softDeleteMetricVersionMetasByMetalakeId(
      @Param("metalakeId") Long metalakeId) {
    return getProvider().softDeleteMetricVersionMetasByMetalakeId(metalakeId);
  }

  public static String deleteMetricVersionMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    return getProvider().deleteMetricVersionMetasByLegacyTimeline(legacyTimeline, limit);
  }

  public static String updateMetricVersionMeta(
      @Param("newMetricVersionMeta") MetricVersionPO newMetricVersionPO,
      @Param("oldMetricVersionMeta") MetricVersionPO oldMetricVersionPO) {
    return getProvider().updateMetricVersionMeta(newMetricVersionPO, oldMetricVersionPO);
  }
}
