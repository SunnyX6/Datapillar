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
import org.apache.gravitino.storage.relational.po.MetricVersionPO;
import org.apache.ibatis.annotations.DeleteProvider;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

public interface MetricVersionMetaMapper {

  String TABLE_NAME = "metric_version_info";

  @InsertProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "insertMetricVersionMeta")
  void insertMetricVersionMeta(@Param("metricVersionMeta") MetricVersionPO metricVersionPO);

  @SelectProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "listMetricVersionMetasByMetricId")
  List<MetricVersionPO> listMetricVersionMetasByMetricId(@Param("metricId") Long metricId);

  @SelectProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "selectMetricVersionMetaById")
  MetricVersionPO selectMetricVersionMetaById(@Param("id") Long id);

  @SelectProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "selectMetricVersionMetaByMetricIdAndVersion")
  MetricVersionPO selectMetricVersionMetaByMetricIdAndVersion(
      @Param("metricId") Long metricId, @Param("version") Integer version);

  @SelectProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "listMetricVersionMetasByRefTableId")
  List<MetricVersionPO> listMetricVersionMetasByRefTableId(@Param("refTableId") Long refTableId);

  @UpdateProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "softDeleteMetricVersionsBySchemaIdAndMetricCode")
  Integer softDeleteMetricVersionsBySchemaIdAndMetricCode(
      @Param("schemaId") Long schemaId, @Param("metricCode") String metricCode);

  @UpdateProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "softDeleteMetricVersionMetaById")
  Integer softDeleteMetricVersionMetaById(@Param("id") Long id);

  @UpdateProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "softDeleteMetricVersionMetasBySchemaId")
  Integer softDeleteMetricVersionMetasBySchemaId(@Param("schemaId") Long schemaId);

  @UpdateProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "softDeleteMetricVersionMetasByCatalogId")
  Integer softDeleteMetricVersionMetasByCatalogId(@Param("catalogId") Long catalogId);

  @UpdateProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "softDeleteMetricVersionMetasByMetalakeId")
  Integer softDeleteMetricVersionMetasByMetalakeId(@Param("metalakeId") Long metalakeId);

  @DeleteProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "deleteMetricVersionMetasByLegacyTimeline")
  Integer deleteMetricVersionMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit);

  @UpdateProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "updateMetricVersionMeta")
  Integer updateMetricVersionMeta(
      @Param("newMetricVersionMeta") MetricVersionPO newMetricVersionPO,
      @Param("oldMetricVersionMeta") MetricVersionPO oldMetricVersionPO);

  @SelectProvider(
      type = MetricVersionMetaSQLProviderFactory.class,
      method = "countMetricVersionsByRefTableId")
  Integer countMetricVersionsByRefTableId(@Param("refTableId") Long refTableId);
}
