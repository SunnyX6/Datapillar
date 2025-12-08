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
package org.apache.gravitino.storage.relational.mapper.provider.base;

import org.apache.gravitino.storage.relational.mapper.MetricMetaMapper;
import org.apache.gravitino.storage.relational.mapper.MetricVersionMetaMapper;
import org.apache.gravitino.storage.relational.po.MetricVersionPO;
import org.apache.ibatis.annotations.Param;

public class MetricVersionMetaBaseSQLProvider {

  public String insertMetricVersionMeta(
      @Param("metricVersionMeta") MetricVersionPO metricVersionPO) {
    return "INSERT INTO "
        + MetricVersionMetaMapper.TABLE_NAME
        + " (metric_id, metalake_id, catalog_id, schema_id, version, metric_name, metric_code,"
        + " metric_type, metric_comment, metric_unit, aggregation_logic, parent_metric_ids,"
        + " calculation_formula, version_properties, audit_info, deleted_at)"
        + " VALUES (#{metricVersionMeta.metricId}, #{metricVersionMeta.metalakeId},"
        + " #{metricVersionMeta.catalogId}, #{metricVersionMeta.schemaId}, #{metricVersionMeta.version},"
        + " #{metricVersionMeta.metricName}, #{metricVersionMeta.metricCode}, #{metricVersionMeta.metricType},"
        + " #{metricVersionMeta.metricComment}, #{metricVersionMeta.metricUnit},"
        + " #{metricVersionMeta.aggregationLogic}, #{metricVersionMeta.parentMetricIds},"
        + " #{metricVersionMeta.calculationFormula}, #{metricVersionMeta.versionProperties},"
        + " #{metricVersionMeta.auditInfo}, #{metricVersionMeta.deletedAt})";
  }

  public String listMetricVersionMetasByMetricId(@Param("metricId") Long metricId) {
    return "SELECT metric_id AS metricId, metalake_id AS metalakeId, catalog_id AS catalogId,"
        + " schema_id AS schemaId, version, metric_name AS metricName, metric_code AS metricCode,"
        + " metric_type AS metricType, metric_comment AS metricComment, metric_unit AS metricUnit,"
        + " aggregation_logic AS aggregationLogic, parent_metric_ids AS parentMetricIds,"
        + " calculation_formula AS calculationFormula, version_properties AS versionProperties,"
        + " audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + MetricVersionMetaMapper.TABLE_NAME
        + " WHERE metric_id = #{metricId} AND deleted_at = 0";
  }

  public String selectMetricVersionMeta(
      @Param("metricId") Long metricId, @Param("version") Integer version) {
    return "SELECT metric_id AS metricId, metalake_id AS metalakeId, catalog_id AS catalogId,"
        + " schema_id AS schemaId, version, metric_name AS metricName, metric_code AS metricCode,"
        + " metric_type AS metricType, metric_comment AS metricComment, metric_unit AS metricUnit,"
        + " aggregation_logic AS aggregationLogic, parent_metric_ids AS parentMetricIds,"
        + " calculation_formula AS calculationFormula, version_properties AS versionProperties,"
        + " audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + MetricVersionMetaMapper.TABLE_NAME
        + " WHERE metric_id = #{metricId} AND version = #{version} AND deleted_at = 0";
  }

  public String softDeleteMetricVersionsBySchemaIdAndMetricCode(
      @Param("schemaId") Long schemaId, @Param("metricCode") String metricCode) {
    return "UPDATE "
        + MetricVersionMetaMapper.TABLE_NAME
        + " mvi SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE mvi.schema_id = #{schemaId} AND mvi.metric_id = ("
        + " SELECT mm.metric_id FROM "
        + MetricMetaMapper.TABLE_NAME
        + " mm WHERE mm.schema_id = #{schemaId} AND mm.metric_code = #{metricCode}"
        + " AND mm.deleted_at = 0) AND mvi.deleted_at = 0";
  }

  public String softDeleteMetricVersionMetaByMetricIdAndVersion(
      @Param("metricId") Long metricId, @Param("version") Integer version) {
    return "UPDATE "
        + MetricVersionMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE metric_id = #{metricId} AND version = #{version} AND deleted_at = 0";
  }

  public String softDeleteMetricVersionMetasBySchemaId(@Param("schemaId") Long schemaId) {
    return "UPDATE "
        + MetricVersionMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String softDeleteMetricVersionMetasByCatalogId(@Param("catalogId") Long catalogId) {
    return "UPDATE "
        + MetricVersionMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE catalog_id = #{catalogId} AND deleted_at = 0";
  }

  public String softDeleteMetricVersionMetasByMetalakeId(@Param("metalakeId") Long metalakeId) {
    return "UPDATE "
        + MetricVersionMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE metalake_id = #{metalakeId} AND deleted_at = 0";
  }

  public String deleteMetricVersionMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    return "DELETE FROM "
        + MetricVersionMetaMapper.TABLE_NAME
        + " WHERE deleted_at > 0 AND deleted_at < #{legacyTimeline} LIMIT #{limit}";
  }

  public String updateMetricVersionMeta(
      @Param("newMetricVersionMeta") MetricVersionPO newMetricVersionPO,
      @Param("oldMetricVersionMeta") MetricVersionPO oldMetricVersionPO) {
    return "UPDATE "
        + MetricVersionMetaMapper.TABLE_NAME
        + " SET "
        + "metric_id = #{newMetricVersionMeta.metricId}, "
        + "metalake_id = #{newMetricVersionMeta.metalakeId}, "
        + "catalog_id = #{newMetricVersionMeta.catalogId}, "
        + "schema_id = #{newMetricVersionMeta.schemaId}, "
        + "version = #{newMetricVersionMeta.version}, "
        + "metric_name = #{newMetricVersionMeta.metricName}, "
        + "metric_code = #{newMetricVersionMeta.metricCode}, "
        + "metric_type = #{newMetricVersionMeta.metricType}, "
        + "metric_comment = #{newMetricVersionMeta.metricComment}, "
        + "metric_unit = #{newMetricVersionMeta.metricUnit}, "
        + "aggregation_logic = #{newMetricVersionMeta.aggregationLogic}, "
        + "parent_metric_ids = #{newMetricVersionMeta.parentMetricIds}, "
        + "calculation_formula = #{newMetricVersionMeta.calculationFormula}, "
        + "version_properties = #{newMetricVersionMeta.versionProperties}, "
        + "audit_info = #{newMetricVersionMeta.auditInfo}, "
        + "deleted_at = #{newMetricVersionMeta.deletedAt} "
        + "WHERE metric_id = #{oldMetricVersionMeta.metricId} "
        + "AND metalake_id = #{oldMetricVersionMeta.metalakeId} "
        + "AND catalog_id = #{oldMetricVersionMeta.catalogId} "
        + "AND schema_id = #{oldMetricVersionMeta.schemaId} "
        + "AND version = #{oldMetricVersionMeta.version} "
        + "AND metric_name = #{oldMetricVersionMeta.metricName} "
        + "AND metric_code = #{oldMetricVersionMeta.metricCode} "
        + "AND metric_type = #{oldMetricVersionMeta.metricType} "
        + "AND metric_comment = #{oldMetricVersionMeta.metricComment} "
        + "AND metric_unit = #{oldMetricVersionMeta.metricUnit} "
        + "AND aggregation_logic = #{oldMetricVersionMeta.aggregationLogic} "
        + "AND parent_metric_ids = #{oldMetricVersionMeta.parentMetricIds} "
        + "AND calculation_formula = #{oldMetricVersionMeta.calculationFormula} "
        + "AND version_properties = #{oldMetricVersionMeta.versionProperties} "
        + "AND audit_info = #{oldMetricVersionMeta.auditInfo} "
        + "AND deleted_at = 0";
  }
}
