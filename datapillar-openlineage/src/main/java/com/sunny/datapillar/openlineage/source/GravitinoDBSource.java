package com.sunny.datapillar.openlineage.source;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.openlineage.model.Catalog;
import com.sunny.datapillar.openlineage.model.Column;
import com.sunny.datapillar.openlineage.model.Metric;
import com.sunny.datapillar.openlineage.model.MetricVersion;
import com.sunny.datapillar.openlineage.model.Modifier;
import com.sunny.datapillar.openlineage.model.Schema;
import com.sunny.datapillar.openlineage.model.Table;
import com.sunny.datapillar.openlineage.model.Tag;
import com.sunny.datapillar.openlineage.model.TagRelation;
import com.sunny.datapillar.openlineage.model.Unit;
import com.sunny.datapillar.openlineage.model.ValueDomain;
import com.sunny.datapillar.openlineage.model.WordRoot;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** JDBC source for Gravitino metadata snapshot rebuild. */
@Component
public class GravitinoDBSource {

  private static final String SQL_CATALOG =
      """
      SELECT catalog_id,metalake_id,catalog_name,catalog_comment
      FROM catalog_meta
      WHERE tenant_id = ? AND deleted_at = 0
      """;
  private static final String SQL_SCHEMA =
      """
      SELECT schema_id,metalake_id,catalog_id,schema_name,schema_comment
      FROM schema_meta
      WHERE tenant_id = ? AND deleted_at = 0
      """;
  private static final String SQL_TABLE =
      """
      SELECT table_id,metalake_id,catalog_id,schema_id,table_name,table_comment
      FROM table_meta
      WHERE tenant_id = ? AND deleted_at = 0
      """;
  private static final String SQL_COLUMN =
      """
      SELECT c.column_id,c.table_id,c.schema_id,c.column_name,c.column_type,c.column_comment
      FROM table_column_version_info c
      JOIN (
        SELECT column_id,MAX(table_version) AS max_table_version
        FROM table_column_version_info
        WHERE tenant_id = ? AND deleted_at = 0
        GROUP BY column_id
      ) latest ON c.column_id = latest.column_id AND c.table_version = latest.max_table_version
      WHERE c.tenant_id = ? AND c.deleted_at = 0 AND c.column_op_type <> 3
      """;
  private static final String SQL_METRIC =
      """
      SELECT metric_id,metalake_id,catalog_id,schema_id,metric_name,metric_code,metric_type,metric_comment
      FROM metric_meta
      WHERE tenant_id = ? AND deleted_at = 0
      """;
  private static final String SQL_METRIC_VERSION =
      """
      SELECT mv.metric_id,mv.ref_table_id,mv.measure_column_ids,mv.filter_column_ids,mv.parent_metric_ids AS parent_metric_codes
      FROM metric_version_info mv
      JOIN metric_meta m ON m.metric_id = mv.metric_id
      WHERE mv.tenant_id = ?
        AND mv.deleted_at = 0
        AND m.tenant_id = ?
        AND m.deleted_at = 0
        AND mv.version = m.current_version
      """;
  private static final String SQL_TAG =
      """
      SELECT tag_id,metalake_id,tag_name,tag_comment AS comment
      FROM tag_meta
      WHERE tenant_id = ? AND deleted_at = 0
      """;
  private static final String SQL_TAG_RELATION =
      """
      SELECT tag_id,metadata_object_id,metadata_object_type
      FROM tag_relation_meta
      WHERE tenant_id = ? AND deleted_at = 0
      """;
  private static final String SQL_WORD_ROOT =
      """
      SELECT root_id,metalake_id,catalog_id,schema_id,root_code,root_name,root_comment
      FROM wordroot_meta
      WHERE tenant_id = ? AND deleted_at = 0
      """;
  private static final String SQL_MODIFIER =
      """
      SELECT modifier_id,metalake_id,catalog_id,schema_id,modifier_code,modifier_name,modifier_comment
      FROM modifier_meta
      WHERE tenant_id = ? AND deleted_at = 0
      """;
  private static final String SQL_UNIT =
      """
      SELECT unit_id,metalake_id,catalog_id,schema_id,unit_code,unit_name,unit_comment
      FROM unit_meta
      WHERE tenant_id = ? AND deleted_at = 0
      """;
  private static final String SQL_VALUE_DOMAIN =
      """
      SELECT domain_id,metalake_id,catalog_id,schema_id,domain_code,domain_name,domain_comment
      FROM value_domain_meta
      WHERE tenant_id = ? AND deleted_at = 0
      """;

  private final JdbcTemplate jdbcTemplate;

  public GravitinoDBSource(@Qualifier("gravitinoJdbcTemplate") JdbcTemplate gravitinoJdbcTemplate) {
    this.jdbcTemplate = gravitinoJdbcTemplate;
  }

  public List<Catalog> listCatalogs(Long tenantId) {
    requireTenantId(tenantId);
    return jdbcTemplate.query(
        SQL_CATALOG,
        (rs, rowNum) -> {
          Catalog row = new Catalog();
          row.setCatalogId(rs.getLong("catalog_id"));
          row.setMetalakeId(rs.getLong("metalake_id"));
          row.setCatalogName(rs.getString("catalog_name"));
          row.setCatalogComment(rs.getString("catalog_comment"));
          return row;
        },
        tenantId);
  }

  public List<Schema> listSchemas(Long tenantId) {
    requireTenantId(tenantId);
    return jdbcTemplate.query(
        SQL_SCHEMA,
        (rs, rowNum) -> {
          Schema row = new Schema();
          row.setSchemaId(rs.getLong("schema_id"));
          row.setMetalakeId(rs.getLong("metalake_id"));
          row.setCatalogId(rs.getLong("catalog_id"));
          row.setSchemaName(rs.getString("schema_name"));
          row.setSchemaComment(rs.getString("schema_comment"));
          return row;
        },
        tenantId);
  }

  public List<Table> listTables(Long tenantId) {
    requireTenantId(tenantId);
    return jdbcTemplate.query(
        SQL_TABLE,
        (rs, rowNum) -> {
          Table row = new Table();
          row.setTableId(rs.getLong("table_id"));
          row.setMetalakeId(rs.getLong("metalake_id"));
          row.setCatalogId(rs.getLong("catalog_id"));
          row.setSchemaId(rs.getLong("schema_id"));
          row.setTableName(rs.getString("table_name"));
          row.setTableComment(rs.getString("table_comment"));
          return row;
        },
        tenantId);
  }

  public List<Column> listColumns(Long tenantId) {
    requireTenantId(tenantId);
    return jdbcTemplate.query(
        SQL_COLUMN,
        (rs, rowNum) -> {
          Column row = new Column();
          row.setColumnId(rs.getLong("column_id"));
          row.setTableId(rs.getLong("table_id"));
          row.setSchemaId(rs.getLong("schema_id"));
          row.setColumnName(rs.getString("column_name"));
          row.setColumnType(rs.getString("column_type"));
          row.setColumnComment(rs.getString("column_comment"));
          return row;
        },
        tenantId,
        tenantId);
  }

  public List<Metric> listMetrics(Long tenantId) {
    requireTenantId(tenantId);
    return jdbcTemplate.query(
        SQL_METRIC,
        (rs, rowNum) -> {
          Metric row = new Metric();
          row.setMetricId(rs.getLong("metric_id"));
          row.setMetalakeId(rs.getLong("metalake_id"));
          row.setCatalogId(rs.getLong("catalog_id"));
          row.setSchemaId(rs.getLong("schema_id"));
          row.setMetricName(rs.getString("metric_name"));
          row.setMetricCode(rs.getString("metric_code"));
          row.setMetricType(rs.getString("metric_type"));
          row.setMetricComment(rs.getString("metric_comment"));
          return row;
        },
        tenantId);
  }

  public List<MetricVersion> listMetricVersions(Long tenantId) {
    requireTenantId(tenantId);
    return jdbcTemplate.query(
        SQL_METRIC_VERSION,
        (rs, rowNum) -> {
          MetricVersion row = new MetricVersion();
          row.setMetricId(rs.getLong("metric_id"));
          row.setRefTableId(rs.getLong("ref_table_id"));
          row.setMeasureColumnIds(rs.getString("measure_column_ids"));
          row.setFilterColumnIds(rs.getString("filter_column_ids"));
          row.setParentMetricCodes(rs.getString("parent_metric_codes"));
          return row;
        },
        tenantId,
        tenantId);
  }

  public List<Tag> listTags(Long tenantId) {
    requireTenantId(tenantId);
    return jdbcTemplate.query(
        SQL_TAG,
        (rs, rowNum) -> {
          Tag row = new Tag();
          row.setTagId(rs.getLong("tag_id"));
          row.setMetalakeId(rs.getLong("metalake_id"));
          row.setTagName(rs.getString("tag_name"));
          row.setComment(rs.getString("comment"));
          return row;
        },
        tenantId);
  }

  public List<TagRelation> listTagRelations(Long tenantId) {
    requireTenantId(tenantId);
    return jdbcTemplate.query(
        SQL_TAG_RELATION,
        (rs, rowNum) -> {
          TagRelation row = new TagRelation();
          row.setTagId(rs.getLong("tag_id"));
          row.setMetadataObjectId(rs.getLong("metadata_object_id"));
          row.setMetadataObjectType(rs.getString("metadata_object_type"));
          return row;
        },
        tenantId);
  }

  public List<WordRoot> listWordRoots(Long tenantId) {
    requireTenantId(tenantId);
    return jdbcTemplate.query(
        SQL_WORD_ROOT,
        (rs, rowNum) -> {
          WordRoot row = new WordRoot();
          row.setRootId(rs.getLong("root_id"));
          row.setMetalakeId(rs.getLong("metalake_id"));
          row.setCatalogId(rs.getLong("catalog_id"));
          row.setSchemaId(rs.getLong("schema_id"));
          row.setRootCode(rs.getString("root_code"));
          row.setRootName(rs.getString("root_name"));
          row.setRootComment(rs.getString("root_comment"));
          return row;
        },
        tenantId);
  }

  public List<Modifier> listModifiers(Long tenantId) {
    requireTenantId(tenantId);
    return jdbcTemplate.query(
        SQL_MODIFIER,
        (rs, rowNum) -> {
          Modifier row = new Modifier();
          row.setModifierId(rs.getLong("modifier_id"));
          row.setMetalakeId(rs.getLong("metalake_id"));
          row.setCatalogId(rs.getLong("catalog_id"));
          row.setSchemaId(rs.getLong("schema_id"));
          row.setModifierCode(rs.getString("modifier_code"));
          row.setModifierName(rs.getString("modifier_name"));
          row.setModifierComment(rs.getString("modifier_comment"));
          return row;
        },
        tenantId);
  }

  public List<Unit> listUnits(Long tenantId) {
    requireTenantId(tenantId);
    return jdbcTemplate.query(
        SQL_UNIT,
        (rs, rowNum) -> {
          Unit row = new Unit();
          row.setUnitId(rs.getLong("unit_id"));
          row.setMetalakeId(rs.getLong("metalake_id"));
          row.setCatalogId(rs.getLong("catalog_id"));
          row.setSchemaId(rs.getLong("schema_id"));
          row.setUnitCode(rs.getString("unit_code"));
          row.setUnitName(rs.getString("unit_name"));
          row.setUnitComment(rs.getString("unit_comment"));
          return row;
        },
        tenantId);
  }

  public List<ValueDomain> listValueDomains(Long tenantId) {
    requireTenantId(tenantId);
    return jdbcTemplate.query(
        SQL_VALUE_DOMAIN,
        (rs, rowNum) -> {
          ValueDomain row = new ValueDomain();
          row.setDomainId(rs.getLong("domain_id"));
          row.setMetalakeId(rs.getLong("metalake_id"));
          row.setCatalogId(rs.getLong("catalog_id"));
          row.setSchemaId(rs.getLong("schema_id"));
          row.setDomainCode(rs.getString("domain_code"));
          row.setDomainName(rs.getString("domain_name"));
          row.setDomainComment(rs.getString("domain_comment"));
          return row;
        },
        tenantId);
  }

  private void requireTenantId(Long tenantId) {
    if (tenantId == null || tenantId <= 0) {
      throw new BadRequestException("tenantId is invalid");
    }
  }
}
