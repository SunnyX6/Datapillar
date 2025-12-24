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

import java.util.List;
import org.apache.gravitino.storage.relational.mapper.MetricMetaMapper;
import org.apache.gravitino.storage.relational.po.MetricPO;
import org.apache.ibatis.annotations.Param;

public class MetricMetaBaseSQLProvider {

  public String insertMetricMeta(@Param("metricMeta") MetricPO metricPO) {
    return "INSERT INTO "
        + MetricMetaMapper.TABLE_NAME
        + " (metric_id, metric_name, metric_code, metric_type, data_type, metalake_id, catalog_id, schema_id,"
        + " metric_comment, current_version, last_version, audit_info, deleted_at)"
        + " VALUES (#{metricMeta.metricId}, #{metricMeta.metricName}, #{metricMeta.metricCode},"
        + " #{metricMeta.metricType}, #{metricMeta.dataType}, #{metricMeta.metalakeId}, #{metricMeta.catalogId},"
        + " #{metricMeta.schemaId}, #{metricMeta.metricComment}, #{metricMeta.currentVersion},"
        + " #{metricMeta.lastVersion}, #{metricMeta.auditInfo}, #{metricMeta.deletedAt})";
  }

  public String insertMetricMetaOnDuplicateKeyUpdate(@Param("metricMeta") MetricPO metricPO) {
    return "INSERT INTO "
        + MetricMetaMapper.TABLE_NAME
        + " (metric_id, metric_name, metric_code, metric_type, data_type, metalake_id, catalog_id, schema_id,"
        + " metric_comment, current_version, last_version, audit_info, deleted_at)"
        + " VALUES (#{metricMeta.metricId}, #{metricMeta.metricName}, #{metricMeta.metricCode},"
        + " #{metricMeta.metricType}, #{metricMeta.dataType}, #{metricMeta.metalakeId}, #{metricMeta.catalogId},"
        + " #{metricMeta.schemaId}, #{metricMeta.metricComment}, #{metricMeta.currentVersion},"
        + " #{metricMeta.lastVersion}, #{metricMeta.auditInfo}, #{metricMeta.deletedAt})"
        + " ON DUPLICATE KEY UPDATE"
        + " metric_name = #{metricMeta.metricName},"
        + " metric_code = #{metricMeta.metricCode},"
        + " metric_type = #{metricMeta.metricType},"
        + " data_type = #{metricMeta.dataType},"
        + " metalake_id = #{metricMeta.metalakeId},"
        + " catalog_id = #{metricMeta.catalogId},"
        + " schema_id = #{metricMeta.schemaId},"
        + " metric_comment = #{metricMeta.metricComment},"
        + " current_version = #{metricMeta.currentVersion},"
        + " last_version = #{metricMeta.lastVersion},"
        + " audit_info = #{metricMeta.auditInfo},"
        + " deleted_at = #{metricMeta.deletedAt}";
  }

  public String listMetricPOsBySchemaId(@Param("schemaId") Long schemaId) {
    return "SELECT metric_id AS metricId, metric_name AS metricName, metric_code AS metricCode,"
        + " metric_type AS metricType, data_type AS dataType, metalake_id AS metalakeId, catalog_id AS catalogId,"
        + " schema_id AS schemaId, metric_comment AS metricComment, current_version AS currentVersion,"
        + " last_version AS lastVersion, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + MetricMetaMapper.TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String listMetricPOsBySchemaIdWithPagination(
      @Param("schemaId") Long schemaId, @Param("offset") int offset, @Param("limit") int limit) {
    return "SELECT metric_id AS metricId, metric_name AS metricName, metric_code AS metricCode,"
        + " metric_type AS metricType, data_type AS dataType, metalake_id AS metalakeId, catalog_id AS catalogId,"
        + " schema_id AS schemaId, metric_comment AS metricComment, current_version AS currentVersion,"
        + " last_version AS lastVersion, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + MetricMetaMapper.TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0"
        + " ORDER BY metric_id"
        + " LIMIT #{limit} OFFSET #{offset}";
  }

  public String countMetricsBySchemaId(@Param("schemaId") Long schemaId) {
    return "SELECT COUNT(*) FROM "
        + MetricMetaMapper.TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String listMetricPOsByMetricIds(List<Long> metricIds) {
    return "<script>"
        + " SELECT metric_id AS metricId, metric_name AS metricName, metric_code AS metricCode,"
        + " metric_type AS metricType, data_type AS dataType, metalake_id AS metalakeId, catalog_id AS catalogId,"
        + " schema_id AS schemaId, metric_comment AS metricComment, current_version AS currentVersion,"
        + " last_version AS lastVersion, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + MetricMetaMapper.TABLE_NAME
        + " WHERE deleted_at = 0"
        + " AND metric_id in ("
        + "<foreach collection='metricIds' item='metricId' separator=','>"
        + "#{metricId}"
        + "</foreach>"
        + ") "
        + "</script>";
  }

  public String selectMetricMetaBySchemaIdAndMetricCode(
      @Param("schemaId") Long schemaId, @Param("metricCode") String metricCode) {
    return "SELECT metric_id AS metricId, metric_name AS metricName, metric_code AS metricCode,"
        + " metric_type AS metricType, data_type AS dataType, metalake_id AS metalakeId, catalog_id AS catalogId,"
        + " schema_id AS schemaId, metric_comment AS metricComment, current_version AS currentVersion,"
        + " last_version AS lastVersion, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + MetricMetaMapper.TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND metric_code = #{metricCode} AND deleted_at = 0";
  }

  public String selectMetricIdBySchemaIdAndMetricCode(
      @Param("schemaId") Long schemaId, @Param("metricCode") String metricCode) {
    return "SELECT metric_id"
        + " FROM "
        + MetricMetaMapper.TABLE_NAME
        + " WHERE schema_id = #{schemaId} AND metric_code = #{metricCode} AND deleted_at = 0";
  }

  public String selectMetricMetaByMetricId(@Param("metricId") Long metricId) {
    return "SELECT metric_id AS metricId, metric_name AS metricName, metric_code AS metricCode,"
        + " metric_type AS metricType, data_type AS dataType, metalake_id AS metalakeId, catalog_id AS catalogId,"
        + " schema_id AS schemaId, metric_comment AS metricComment, current_version AS currentVersion,"
        + " last_version AS lastVersion, audit_info AS auditInfo, deleted_at AS deletedAt"
        + " FROM "
        + MetricMetaMapper.TABLE_NAME
        + " WHERE metric_id = #{metricId} AND deleted_at = 0";
  }

  public String softDeleteMetricMetaBySchemaIdAndMetricCode(
      @Param("schemaId") Long schemaId, @Param("metricCode") String metricCode) {
    return "UPDATE "
        + MetricMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND metric_code = #{metricCode} AND deleted_at = 0";
  }

  public String softDeleteMetricMetasByCatalogId(@Param("catalogId") Long catalogId) {
    return "UPDATE "
        + MetricMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE catalog_id = #{catalogId} AND deleted_at = 0";
  }

  public String softDeleteMetricMetasByMetalakeId(@Param("metalakeId") Long metalakeId) {
    return "UPDATE "
        + MetricMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE metalake_id = #{metalakeId} AND deleted_at = 0";
  }

  public String softDeleteMetricMetasBySchemaId(@Param("schemaId") Long schemaId) {
    return "UPDATE "
        + MetricMetaMapper.TABLE_NAME
        + " SET deleted_at = (UNIX_TIMESTAMP() * 1000.0)"
        + " + EXTRACT(MICROSECOND FROM CURRENT_TIMESTAMP(3)) / 1000"
        + " WHERE schema_id = #{schemaId} AND deleted_at = 0";
  }

  public String deleteMetricMetasByLegacyTimeline(
      @Param("legacyTimeline") Long legacyTimeline, @Param("limit") int limit) {
    return "DELETE FROM "
        + MetricMetaMapper.TABLE_NAME
        + " WHERE deleted_at > 0 AND deleted_at < #{legacyTimeline} LIMIT #{limit}";
  }

  public String updateMetricMeta(
      @Param("newMetricMeta") MetricPO newMetricPO, @Param("oldMetricMeta") MetricPO oldMetricPO) {
    return "UPDATE "
        + MetricMetaMapper.TABLE_NAME
        + " SET metric_name = #{newMetricMeta.metricName},"
        + " metric_code = #{newMetricMeta.metricCode},"
        + " metric_type = #{newMetricMeta.metricType},"
        + " data_type = #{newMetricMeta.dataType},"
        + " metalake_id = #{newMetricMeta.metalakeId},"
        + " catalog_id = #{newMetricMeta.catalogId},"
        + " schema_id = #{newMetricMeta.schemaId},"
        + " metric_comment = #{newMetricMeta.metricComment},"
        + " current_version = #{newMetricMeta.currentVersion},"
        + " last_version = #{newMetricMeta.lastVersion},"
        + " audit_info = #{newMetricMeta.auditInfo},"
        + " deleted_at = #{newMetricMeta.deletedAt}"
        + " WHERE metric_id = #{oldMetricMeta.metricId}"
        + " AND current_version = #{oldMetricMeta.currentVersion}"
        + " AND deleted_at = 0";
  }

  /** 原子递增 metric 的 last_version 和 current_version */
  public String updateMetricLastVersion(@Param("metricId") Long metricId) {
    return "UPDATE "
        + MetricMetaMapper.TABLE_NAME
        + " SET last_version = last_version + 1, current_version = last_version + 1"
        + " WHERE metric_id = #{metricId} AND deleted_at = 0";
  }
}
