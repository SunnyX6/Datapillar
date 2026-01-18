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
package org.apache.gravitino.dto.requests;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.rest.RESTRequest;

/** 更新指定指标版本信息的请求 */
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class MetricVersionUpdateRequest implements RESTRequest {

  @JsonProperty("metricName")
  private String metricName;

  @JsonProperty("metricCode")
  private String metricCode;

  @JsonProperty("metricType")
  private String metricType;

  @JsonProperty("dataType")
  private String dataType;

  @JsonProperty("comment")
  private String comment;

  @JsonProperty("unit")
  private String unit;

  @JsonProperty("unitName")
  private String unitName;

  @JsonProperty("parentMetricCodes")
  private String[] parentMetricCodes;

  @JsonProperty("calculationFormula")
  private String calculationFormula;

  @JsonProperty("refTableId")
  private Long refTableId;

  @JsonProperty("measureColumnIds")
  private String measureColumnIds;

  @JsonProperty("filterColumnIds")
  private String filterColumnIds;

  /** 允许的数值数据类型 */
  private static final java.util.Set<String> NUMERIC_DATA_TYPES =
      java.util.Set.of("BYTE", "SHORT", "INTEGER", "LONG", "FLOAT", "DOUBLE", "DECIMAL");

  @Override
  public void validate() throws IllegalArgumentException {
    Preconditions.checkArgument(
        StringUtils.isNotBlank(metricName), "\"metricName\" field is required and cannot be empty");
    Preconditions.checkArgument(
        StringUtils.isNotBlank(metricCode), "\"metricCode\" field is required and cannot be empty");
    Preconditions.checkArgument(
        StringUtils.isNotBlank(metricType), "\"metricType\" field is required and cannot be empty");
    Preconditions.checkArgument(
        StringUtils.isNotBlank(dataType), "\"dataType\" field is required and cannot be empty");

    // 校验 dataType 必须是数值类型
    String baseType =
        dataType.contains("(") ? dataType.substring(0, dataType.indexOf("(")) : dataType;
    Preconditions.checkArgument(
        NUMERIC_DATA_TYPES.contains(baseType.toUpperCase()),
        "\"dataType\" must be a numeric type (BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL), but got: %s",
        dataType);

    Preconditions.checkArgument(
        StringUtils.isNotBlank(unit), "\"unit\" field is required and cannot be empty");
    Preconditions.checkArgument(
        StringUtils.isNotBlank(calculationFormula),
        "\"calculationFormula\" field is required and cannot be empty");

    Metric.Type type = Metric.Type.valueOf(metricType.toUpperCase());

    // ATOMIC 类型必须指定数据源引用
    if (type == Metric.Type.ATOMIC) {
      Preconditions.checkArgument(
          refTableId != null, "\"refTableId\" is required for ATOMIC metric type");
    }

    // DERIVED 和 COMPOSITE 类型必须指定 parentMetricCodes
    if (type == Metric.Type.DERIVED || type == Metric.Type.COMPOSITE) {
      Preconditions.checkArgument(
          parentMetricCodes != null && parentMetricCodes.length > 0,
          "\"parentMetricCodes\" is required for %s metric type",
          type.name());
    }
  }
}
