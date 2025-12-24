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
import org.apache.gravitino.storage.relational.po.MetricPO;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

public interface MetricMetaMapper {
  String TABLE_NAME = "metric_meta";

  @InsertProvider(type = MetricMetaSQLProviderFactory.class, method = "insertMetricMeta")
  void insertMetricMeta(@Param("metricMeta") MetricPO metricPO);

  @InsertProvider(
      type = MetricMetaSQLProviderFactory.class,
      method = "insertMetricMetaOnDuplicateKeyUpdate")
  void insertMetricMetaOnDuplicateKeyUpdate(@Param("metricMeta") MetricPO metricPO);

  @SelectProvider(type = MetricMetaSQLProviderFactory.class, method = "listMetricPOsBySchemaId")
  List<MetricPO> listMetricPOsBySchemaId(@Param("schemaId") Long schemaId);

  @SelectProvider(
      type = MetricMetaSQLProviderFactory.class,
      method = "listMetricPOsBySchemaIdWithPagination")
  List<MetricPO> listMetricPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit);

  @SelectProvider(type = MetricMetaSQLProviderFactory.class, method = "countMetricsBySchemaId")
  long countMetricsBySchemaId(@Param("schemaId") Long schemaId);

  @SelectProvider(type = MetricMetaSQLProviderFactory.class, method = "listMetricPOsByMetricIds")
  List<MetricPO> listMetricPOsByMetricIds(@Param("metricIds") List<Long> metricIds);

  @SelectProvider(
      type = MetricMetaSQLProviderFactory.class,
      method = "selectMetricMetaBySchemaIdAndMetricCode")
  MetricPO selectMetricMetaBySchemaIdAndMetricCode(
      @Param("schemaId") Long schemaId, @Param("metricCode") String metricCode);

  @SelectProvider(
      type = MetricMetaSQLProviderFactory.class,
      method = "selectMetricIdBySchemaIdAndMetricCode")
  Long selectMetricIdBySchemaIdAndMetricCode(
      @Param("schemaId") Long schemaId, @Param("metricCode") String metricCode);

  @SelectProvider(type = MetricMetaSQLProviderFactory.class, method = "selectMetricMetaByMetricId")
  MetricPO selectMetricMetaByMetricId(@Param("metricId") Long metricId);

  @UpdateProvider(
      type = MetricMetaSQLProviderFactory.class,
      method = "softDeleteMetricMetaBySchemaIdAndMetricCode")
  Integer softDeleteMetricMetaBySchemaIdAndMetricCode(
      @Param("schemaId") Long schemaId, @Param("metricCode") String metricCode);

  @UpdateProvider(
      type = MetricMetaSQLProviderFactory.class,
      method = "softDeleteMetricMetasByCatalogId")
  Integer softDeleteMetricMetasByCatalogId(@Param("catalogId") Long catalogId);

  @UpdateProvider(
      type = MetricMetaSQLProviderFactory.class,
      method = "softDeleteMetricMetasByMetalakeId")
  Integer softDeleteMetricMetasByMetalakeId(@Param("metalakeId") Long metalakeId);

  @UpdateProvider(
      type = MetricMetaSQLProviderFactory.class,
      method = "softDeleteMetricMetasBySchemaId")
  Integer softDeleteMetricMetasBySchemaId(@Param("schemaId") Long schemaId);

  @DeleteProvider(
      type = MetricMetaSQLProviderFactory.class,
      method = "deleteMetricMetasByLegacyTimeline")
  Integer deleteMetricMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit);

  @UpdateProvider(type = MetricMetaSQLProviderFactory.class, method = "updateMetricMeta")
  Integer updateMetricMeta(
      @Param("newMetricMeta") MetricPO newMetricPO, @Param("oldMetricMeta") MetricPO oldMetricPO);

  @UpdateProvider(type = MetricMetaSQLProviderFactory.class, method = "updateMetricLastVersion")
  Integer updateMetricLastVersion(@Param("metricId") Long metricId);
}
