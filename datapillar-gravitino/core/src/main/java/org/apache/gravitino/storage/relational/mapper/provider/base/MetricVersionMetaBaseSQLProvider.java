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
        + " metric_type, data_type, metric_comment, metric_unit, parent_metric_codes,"
        + " calculation_formula, ref_catalog_name, ref_schema_name, ref_table_name, measure_columns, filter_columns,"
        + " version_properties, audit_info, deleted_at)"
        + " VALUES (#{metricVersionMeta.metricId}, #{metricVersionMeta.metalakeId},"
        + " #{metricVersionMeta.catalogId}, #{metricVersionMeta.schemaId}, #{metricVersionMeta.version},"
        + " #{metricVersionMeta.metricName}, #{metricVersionMeta.metricCode}, #{metricVersionMeta.metricType},"
        + " #{metricVersionMeta.dataType}, #{metricVersionMeta.metricComment}, #{metricVersionMeta.metricUnit},"
        + " #{metricVersionMeta.parentMetricCodes},"
        + " #{metricVersionMeta.calculationFormula}, #{metricVersionMeta.refCatalogName},"
        + " #{metricVersionMeta.refSchemaName}, #{metricVersionMeta.refTableName}, #{metricVersionMeta.measureColumns},"
        + " #{metricVersionMeta.filterColumns}, #{metricVersionMeta.versionProperties}, #{metricVersionMeta.auditInfo}, #{metricVersionMeta.deletedAt})";
  }

  public String listMetricVersionMetasByMetricId(@Param("metricId") Long metricId) {
    return "SELECT mv.id, mv.metric_id AS metricId, mv.metalake_id AS metalakeId, mv.catalog_id AS catalogId,"
        + " mv.schema_id AS schemaId, mv.version, mv.metric_name AS metricName, mv.metric_code AS metricCode,"
        + " mv.metric_type AS metricType, mv.data_type AS dataType, mv.metric_comment AS metricComment,"
        + " mv.metric_unit AS metricUnit, u.unit_name AS unitName, u.unit_symbol AS unitSymbol,"
        + " mv.parent_metric_codes AS parentMetricCodes,"
        + " mv.calculation_formula AS calculationFormula, mv.ref_catalog_name AS refCatalogName,"
        + " mv.ref_schema_name AS refSchemaName, mv.ref_table_name AS refTableName, mv.measure_columns AS measureColumns,"
        + " mv.filter_columns AS filterColumns, mv.version_properties AS versionProperties, mv.audit_info AS auditInfo, mv.deleted_at AS deletedAt"
        + " FROM "
        + MetricVersionMetaMapper.TABLE_NAME
        + " mv LEFT JOIN unit_meta u ON mv.schema_id = u.schema_id AND mv.metric_unit = u.unit_code AND u.deleted_at = 0"
        + " WHERE mv.metric_id = #{metricId} AND mv.deleted_at = 0"
        + " ORDER BY mv.version DESC";
  }

  public String selectMetricVersionMetaById(@Param("id") Long id) {
    return "SELECT mv.id, mv.metric_id AS metricId, mv.metalake_id AS metalakeId, mv.catalog_id AS catalogId,"
        + " mv.schema_id AS schemaId, mv.version, mv.metric_name AS metricName, mv.metric_code AS metricCode,"
        + " mv.metric_type AS metricType, mv.data_type AS dataType, mv.metric_comment AS metricComment,"
        + " mv.metric_unit AS metricUnit, u.unit_name AS unitName, u.unit_symbol AS unitSymbol,"
        + " mv.parent_metric_codes AS parentMetricCodes,"
        + " mv.calculation_formula AS calculationFormula, mv.ref_catalog_name AS refCatalogName,"
        + " mv.ref_schema_name AS refSchemaName, mv.ref_table_name AS refTableName, mv.measure_columns AS measureColumns,"
        + " mv.filter_columns AS filterColumns, mv.version_properties AS versionProperties, mv.audit_info AS auditInfo, mv.deleted_at AS deletedAt"
        + " FROM "
        + MetricVersionMetaMapper.TABLE_NAME
        + " mv LEFT JOIN unit_meta u ON mv.schema_id = u.schema_id AND mv.metric_unit = u.unit_code AND u.deleted_at = 0"
        + " WHERE mv.id = #{id} AND mv.deleted_at = 0";
  }

  public String selectMetricVersionMetaByMetricIdAndVersion(
      @Param("metricId") Long metricId, @Param("version") Integer version) {
    return "SELECT mv.id, mv.metric_id AS metricId, mv.metalake_id AS metalakeId, mv.catalog_id AS catalogId,"
        + " mv.schema_id AS schemaId, mv.version, mv.metric_name AS metricName, mv.metric_code AS metricCode,"
        + " mv.metric_type AS metricType, mv.data_type AS dataType, mv.metric_comment AS metricComment,"
        + " mv.metric_unit AS metricUnit, u.unit_name AS unitName, u.unit_symbol AS unitSymbol,"
        + " mv.parent_metric_codes AS parentMetricCodes,"
        + " mv.calculation_formula AS calculationFormula, mv.ref_catalog_name AS refCatalogName,"
        + " mv.ref_schema_name AS refSchemaName, mv.ref_table_name AS refTableName, mv.measure_columns AS measureColumns,"
        + " mv.filter_columns AS filterColumns, mv.version_properties AS versionProperties, mv.audit_info AS auditInfo, mv.deleted_at AS deletedAt"
        + " FROM "
        + MetricVersionMetaMapper.TABLE_NAME
        + " mv LEFT JOIN unit_meta u ON mv.schema_id = u.schema_id AND mv.metric_unit = u.unit_code AND u.deleted_at = 0"
        + " WHERE mv.metric_id = #{metricId} AND mv.version = #{version} AND mv.deleted_at = 0";
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

  public String softDeleteMetricVersionMetaById(@Param("id") Long id) {
    return "UPDATE "
        + MetricVersionMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE id = #{id} AND deleted_at = 0";
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
        + "data_type = #{newMetricVersionMeta.dataType}, "
        + "metric_comment = #{newMetricVersionMeta.metricComment}, "
        + "metric_unit = #{newMetricVersionMeta.metricUnit}, "
        + "parent_metric_codes = #{newMetricVersionMeta.parentMetricCodes}, "
        + "calculation_formula = #{newMetricVersionMeta.calculationFormula}, "
        + "ref_catalog_name = #{newMetricVersionMeta.refCatalogName}, "
        + "ref_schema_name = #{newMetricVersionMeta.refSchemaName}, "
        + "ref_table_name = #{newMetricVersionMeta.refTableName}, "
        + "measure_columns = #{newMetricVersionMeta.measureColumns}, "
        + "filter_columns = #{newMetricVersionMeta.filterColumns}, "
        + "version_properties = #{newMetricVersionMeta.versionProperties}, "
        + "audit_info = #{newMetricVersionMeta.auditInfo}, "
        + "deleted_at = #{newMetricVersionMeta.deletedAt} "
        + "WHERE id = #{oldMetricVersionMeta.id} "
        + "AND deleted_at = 0";
  }
}
