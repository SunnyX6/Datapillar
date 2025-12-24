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
package org.apache.gravitino.meta;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.ToString;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.Entity;
import org.apache.gravitino.Field;
import org.apache.gravitino.HasIdentifier;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dataset.MetricVersion;

@ToString
public class MetricVersionEntity implements Entity, Auditable, HasIdentifier, MetricVersion {

  public static final Field METRIC_IDENT =
      Field.required("metric_ident", NameIdentifier.class, "指标的名称标识符");
  public static final Field VERSION = Field.required("version", Integer.class, "指标版本号");
  public static final Field METRIC_NAME = Field.required("metric_name", String.class, "指标名称快照");
  public static final Field METRIC_CODE = Field.required("metric_code", String.class, "指标编码快照");
  public static final Field METRIC_TYPE =
      Field.required("metric_type", Metric.Type.class, "指标类型快照");
  public static final Field DATA_TYPE = Field.optional("data_type", String.class, "数据类型快照");
  public static final Field COMMENT = Field.optional("comment", String.class, "指标版本注释");
  public static final Field UNIT = Field.optional("unit", String.class, "指标单位");
  public static final Field AGGREGATION_LOGIC =
      Field.optional("aggregation_logic", String.class, "聚合逻辑");
  public static final Field PARENT_METRIC_IDS =
      Field.optional("parent_metric_ids", Long[].class, "父指标ID数组");
  public static final Field CALCULATION_FORMULA =
      Field.optional("calculation_formula", String.class, "计算公式");
  public static final Field REF_CATALOG_NAME =
      Field.optional("ref_catalog_name", String.class, "引用的Catalog名称（原子指标）");
  public static final Field REF_SCHEMA_NAME =
      Field.optional("ref_schema_name", String.class, "引用的Schema名称（原子指标）");
  public static final Field REF_TABLE_NAME =
      Field.optional("ref_table_name", String.class, "引用的Table名称（原子指标）");
  public static final Field MEASURE_COLUMNS =
      Field.optional("measure_columns", String.class, "度量列JSON数组");
  public static final Field FILTER_COLUMNS =
      Field.optional("filter_columns", String.class, "过滤列JSON数组");
  public static final Field PROPERTIES = Field.optional("properties", Map.class, "版本属性");
  public static final Field AUDIT_INFO = Field.required("audit_info", AuditInfo.class, "审计信息");

  private NameIdentifier metricIdent;
  private Integer version;
  private String metricName;
  private String metricCode;
  private Metric.Type metricType;
  private String dataType;
  private String comment;
  private String unit;
  private String aggregationLogic;
  private Long[] parentMetricIds;
  private String calculationFormula;
  private String refCatalogName;
  private String refSchemaName;
  private String refTableName;
  private String measureColumns;
  private String filterColumns;
  private AuditInfo auditInfo;
  private Map<String, String> properties;

  private MetricVersionEntity() {}

  @Override
  public Map<Field, Object> fields() {
    Map<Field, Object> fields = Maps.newHashMap();
    fields.put(METRIC_IDENT, metricIdent);
    fields.put(VERSION, version);
    fields.put(METRIC_NAME, metricName);
    fields.put(METRIC_CODE, metricCode);
    fields.put(METRIC_TYPE, metricType);
    fields.put(DATA_TYPE, dataType);
    fields.put(COMMENT, comment);
    fields.put(UNIT, unit);
    fields.put(AGGREGATION_LOGIC, aggregationLogic);
    fields.put(PARENT_METRIC_IDS, parentMetricIds);
    fields.put(CALCULATION_FORMULA, calculationFormula);
    fields.put(REF_CATALOG_NAME, refCatalogName);
    fields.put(REF_SCHEMA_NAME, refSchemaName);
    fields.put(REF_TABLE_NAME, refTableName);
    fields.put(MEASURE_COLUMNS, measureColumns);
    fields.put(FILTER_COLUMNS, filterColumns);
    fields.put(PROPERTIES, properties);
    fields.put(AUDIT_INFO, auditInfo);
    return Collections.unmodifiableMap(fields);
  }

  public NameIdentifier metricIdentifier() {
    return metricIdent;
  }

  @Override
  public int version() {
    return version;
  }

  @Override
  public String metricName() {
    return metricName;
  }

  @Override
  public String metricCode() {
    return metricCode;
  }

  @Override
  public Metric.Type metricType() {
    return metricType;
  }

  @Override
  public String dataType() {
    return dataType;
  }

  @Override
  public String comment() {
    return comment;
  }

  @Override
  public String unit() {
    return unit;
  }

  @Override
  public String aggregationLogic() {
    return aggregationLogic;
  }

  @Override
  public Long[] parentMetricIds() {
    return parentMetricIds;
  }

  @Override
  public String calculationFormula() {
    return calculationFormula;
  }

  @Override
  public String refCatalogName() {
    return refCatalogName;
  }

  @Override
  public String refSchemaName() {
    return refSchemaName;
  }

  @Override
  public String refTableName() {
    return refTableName;
  }

  @Override
  public String measureColumns() {
    return measureColumns;
  }

  @Override
  public String filterColumns() {
    return filterColumns;
  }

  @Override
  public Map<String, String> properties() {
    return properties;
  }

  @Override
  public AuditInfo auditInfo() {
    return auditInfo;
  }

  @Override
  public EntityType type() {
    return EntityType.METRIC_VERSION;
  }

  @Override
  public String name() {
    return String.valueOf(version);
  }

  @Override
  public Namespace namespace() {
    List<String> levels = Lists.newArrayList(metricIdent.namespace().levels());
    levels.add(metricIdent.name());
    return Namespace.of(levels.toArray(new String[0]));
  }

  @Override
  public Long id() {
    throw new UnsupportedOperationException("Metric version entity does not have an ID.");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MetricVersionEntity)) {
      return false;
    }
    MetricVersionEntity that = (MetricVersionEntity) o;
    return Objects.equals(version, that.version)
        && Objects.equals(metricIdent, that.metricIdent)
        && Objects.equals(metricName, that.metricName)
        && Objects.equals(metricCode, that.metricCode)
        && metricType == that.metricType
        && Objects.equals(dataType, that.dataType)
        && Objects.equals(comment, that.comment)
        && Objects.equals(unit, that.unit)
        && Objects.equals(aggregationLogic, that.aggregationLogic)
        && Objects.deepEquals(parentMetricIds, that.parentMetricIds)
        && Objects.equals(calculationFormula, that.calculationFormula)
        && Objects.equals(refCatalogName, that.refCatalogName)
        && Objects.equals(refSchemaName, that.refSchemaName)
        && Objects.equals(refTableName, that.refTableName)
        && Objects.equals(measureColumns, that.measureColumns)
        && Objects.equals(filterColumns, that.filterColumns)
        && Objects.equals(properties, that.properties)
        && Objects.equals(auditInfo, that.auditInfo);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            metricIdent,
            version,
            metricName,
            metricCode,
            metricType,
            dataType,
            comment,
            unit,
            aggregationLogic,
            calculationFormula,
            refCatalogName,
            refSchemaName,
            refTableName,
            measureColumns,
            filterColumns,
            properties,
            auditInfo);
    result = 31 * result + Arrays.hashCode(parentMetricIds);
    return result;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final MetricVersionEntity metricVersion;

    private Builder() {
      metricVersion = new MetricVersionEntity();
    }

    public Builder withMetricIdentifier(NameIdentifier metricIdent) {
      metricVersion.metricIdent = metricIdent;
      return this;
    }

    public Builder withVersion(int version) {
      metricVersion.version = version;
      return this;
    }

    public Builder withMetricName(String metricName) {
      metricVersion.metricName = metricName;
      return this;
    }

    public Builder withMetricCode(String metricCode) {
      metricVersion.metricCode = metricCode;
      return this;
    }

    public Builder withMetricType(Metric.Type metricType) {
      metricVersion.metricType = metricType;
      return this;
    }

    public Builder withDataType(String dataType) {
      metricVersion.dataType = dataType;
      return this;
    }

    public Builder withComment(String comment) {
      metricVersion.comment = comment;
      return this;
    }

    public Builder withUnit(String unit) {
      metricVersion.unit = unit;
      return this;
    }

    public Builder withAggregationLogic(String aggregationLogic) {
      metricVersion.aggregationLogic = aggregationLogic;
      return this;
    }

    public Builder withParentMetricIds(Long[] parentMetricIds) {
      metricVersion.parentMetricIds = parentMetricIds;
      return this;
    }

    public Builder withCalculationFormula(String calculationFormula) {
      metricVersion.calculationFormula = calculationFormula;
      return this;
    }

    public Builder withRefCatalogName(String refCatalogName) {
      metricVersion.refCatalogName = refCatalogName;
      return this;
    }

    public Builder withRefSchemaName(String refSchemaName) {
      metricVersion.refSchemaName = refSchemaName;
      return this;
    }

    public Builder withRefTableName(String refTableName) {
      metricVersion.refTableName = refTableName;
      return this;
    }

    public Builder withMeasureColumns(String measureColumns) {
      metricVersion.measureColumns = measureColumns;
      return this;
    }

    public Builder withFilterColumns(String filterColumns) {
      metricVersion.filterColumns = filterColumns;
      return this;
    }

    public Builder withProperties(Map<String, String> properties) {
      metricVersion.properties = properties;
      return this;
    }

    public Builder withAuditInfo(AuditInfo auditInfo) {
      metricVersion.auditInfo = auditInfo;
      return this;
    }

    public MetricVersionEntity build() {
      metricVersion.validate();
      return metricVersion;
    }
  }
}
