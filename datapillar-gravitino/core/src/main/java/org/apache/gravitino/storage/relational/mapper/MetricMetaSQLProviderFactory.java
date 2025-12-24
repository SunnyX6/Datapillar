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
import org.apache.gravitino.storage.relational.mapper.provider.base.MetricMetaBaseSQLProvider;
import org.apache.gravitino.storage.relational.po.MetricPO;
import org.apache.gravitino.storage.relational.session.SqlSessionFactoryHelper;
import org.apache.ibatis.annotations.Param;

public class MetricMetaSQLProviderFactory {

  static class MetricMetaMySQLProvider extends MetricMetaBaseSQLProvider {}

  static class MetricMetaH2Provider extends MetricMetaBaseSQLProvider {}

  private static final Map<JDBCBackendType, MetricMetaBaseSQLProvider>
      METRIC_META_SQL_PROVIDER_MAP =
          ImmutableMap.of(
              JDBCBackendType.MYSQL, new MetricMetaMySQLProvider(),
              JDBCBackendType.H2, new MetricMetaH2Provider());

  public static MetricMetaBaseSQLProvider getProvider() {
    String databaseId =
        SqlSessionFactoryHelper.getInstance()
            .getSqlSessionFactory()
            .getConfiguration()
            .getDatabaseId();

    JDBCBackendType jdbcBackendType = JDBCBackendType.fromString(databaseId);
    return METRIC_META_SQL_PROVIDER_MAP.get(jdbcBackendType);
  }

  public static String insertMetricMeta(@Param("metricMeta") MetricPO metricPO) {
    return getProvider().insertMetricMeta(metricPO);
  }

  public static String insertMetricMetaOnDuplicateKeyUpdate(
      @Param("metricMeta") MetricPO metricPO) {
    return getProvider().insertMetricMetaOnDuplicateKeyUpdate(metricPO);
  }

  public static String listMetricPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().listMetricPOsBySchemaId(schemaId);
  }

  public static String listMetricPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit) {
    return getProvider().listMetricPOsBySchemaIdWithPagination(schemaId, offset, limit);
  }

  public static String countMetricsBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().countMetricsBySchemaId(schemaId);
  }

  public static String listMetricPOsByMetricIds(@Param("metricIds") List<Long> metricIds) {
    return getProvider().listMetricPOsByMetricIds(metricIds);
  }

  public static String selectMetricMetaBySchemaIdAndMetricCode(
      @Param("schemaId") Long schemaId, @Param("metricCode") String metricCode) {
    return getProvider().selectMetricMetaBySchemaIdAndMetricCode(schemaId, metricCode);
  }

  public static String selectMetricIdBySchemaIdAndMetricCode(
      @Param("schemaId") Long schemaId, @Param("metricCode") String metricCode) {
    return getProvider().selectMetricIdBySchemaIdAndMetricCode(schemaId, metricCode);
  }

  public static String selectMetricMetaByMetricId(@Param("metricId") Long metricId) {
    return getProvider().selectMetricMetaByMetricId(metricId);
  }

  public static String softDeleteMetricMetaBySchemaIdAndMetricCode(
      @Param("schemaId") Long schemaId, @Param("metricCode") String metricCode) {
    return getProvider().softDeleteMetricMetaBySchemaIdAndMetricCode(schemaId, metricCode);
  }

  public static String softDeleteMetricMetasByCatalogId(@Param("catalogId") Long catalogId) {
    return getProvider().softDeleteMetricMetasByCatalogId(catalogId);
  }

  public static String softDeleteMetricMetasByMetalakeId(@Param("metalakeId") Long metalakeId) {
    return getProvider().softDeleteMetricMetasByMetalakeId(metalakeId);
  }

  public static String softDeleteMetricMetasBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().softDeleteMetricMetasBySchemaId(schemaId);
  }

  public static String deleteMetricMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    return getProvider().deleteMetricMetasByLegacyTimeline(legacyTimeline, limit);
  }

  public static String updateMetricMeta(
      @Param("newMetricMeta") MetricPO newMetricPO, @Param("oldMetricMeta") MetricPO oldMetricPO) {
    return getProvider().updateMetricMeta(newMetricPO, oldMetricPO);
  }

  public static String updateMetricLastVersion(@Param("metricId") Long metricId) {
    return getProvider().updateMetricLastVersion(metricId);
  }
}
