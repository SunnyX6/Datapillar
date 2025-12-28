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
import org.apache.gravitino.storage.relational.mapper.provider.base.MetricModifierMetaBaseSQLProvider;
import org.apache.gravitino.storage.relational.mapper.provider.postgresql.MetricModifierMetaPostgreSQLProvider;
import org.apache.gravitino.storage.relational.po.MetricModifierPO;
import org.apache.gravitino.storage.relational.session.SqlSessionFactoryHelper;
import org.apache.ibatis.annotations.Param;

public class MetricModifierMetaSQLProviderFactory {
  private static final Map<JDBCBackendType, MetricModifierMetaBaseSQLProvider>
      METRIC_MODIFIER_META_SQL_PROVIDER_MAP =
          ImmutableMap.of(
              JDBCBackendType.MYSQL, new MetricModifierMetaMySQLProvider(),
              JDBCBackendType.H2, new MetricModifierMetaH2Provider(),
              JDBCBackendType.POSTGRESQL, new MetricModifierMetaPostgreSQLProvider());

  public static MetricModifierMetaBaseSQLProvider getProvider() {
    String databaseId =
        SqlSessionFactoryHelper.getInstance()
            .getSqlSessionFactory()
            .getConfiguration()
            .getDatabaseId();

    JDBCBackendType jdbcBackendType = JDBCBackendType.fromString(databaseId);
    return METRIC_MODIFIER_META_SQL_PROVIDER_MAP.get(jdbcBackendType);
  }

  static class MetricModifierMetaMySQLProvider extends MetricModifierMetaBaseSQLProvider {}

  static class MetricModifierMetaH2Provider extends MetricModifierMetaBaseSQLProvider {}

  public static String listMetricModifierPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().listMetricModifierPOsBySchemaId(schemaId);
  }

  public static String listMetricModifierPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit) {
    return getProvider().listMetricModifierPOsBySchemaIdWithPagination(schemaId, offset, limit);
  }

  public static String countMetricModifiersBySchemaId(@Param("schemaId") Long schemaId) {
    return getProvider().countMetricModifiersBySchemaId(schemaId);
  }

  public static String selectMetricModifierMetaBySchemaIdAndModifierCode(
      @Param("schemaId") Long schemaId, @Param("modifierCode") String modifierCode) {
    return getProvider().selectMetricModifierMetaBySchemaIdAndModifierCode(schemaId, modifierCode);
  }

  public static String listMetricModifierPOsByModifierIds(
      @Param("modifierIds") List<Long> modifierIds) {
    return getProvider().listMetricModifierPOsByModifierIds(modifierIds);
  }

  public static String updateMetricModifierMeta(
      @Param("newMetricModifier") MetricModifierPO newMetricModifierPO,
      @Param("oldMetricModifier") MetricModifierPO oldMetricModifierPO) {
    return getProvider().updateMetricModifierMeta(newMetricModifierPO, oldMetricModifierPO);
  }
}
