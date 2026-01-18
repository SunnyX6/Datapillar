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
package org.apache.gravitino.dataset;

import java.util.Collections;
import java.util.Map;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.annotation.Evolving;

/** 表示指标 {@link Metric} 的单个版本快照。 指标版本是指标在某个时间点的快照，包含指标的所有属性和计算逻辑。 */
@Evolving
public interface MetricVersion extends Auditable {

  /**
   * @return 版本ID（自增主键）
   */
  Long id();

  /**
   * @return 版本号，从1开始
   */
  Integer version();

  /**
   * @return 指标名称快照
   */
  String metricName();

  /**
   * @return 指标编码快照
   */
  String metricCode();

  /**
   * @return 指标类型快照
   */
  Metric.Type metricType();

  /**
   * @return 指标注释快照，如果未设置则返回 null
   */
  default String comment() {
    return null;
  }

  /**
   * @return 数据类型快照，如 STRING, INTEGER, DECIMAL(10,2)，如果未设置则返回 null
   */
  default String dataType() {
    return null;
  }

  /**
   * @return 指标单位编码，如果未设置则返回 null
   */
  default String unit() {
    return null;
  }

  /**
   * @return 指标单位名称，如果未设置则返回 null
   */
  default String unitName() {
    return null;
  }

  /**
   * @return 指标单位符号，如 ¥、$、%，如果未设置则返回 null
   */
  default String unitSymbol() {
    return null;
  }

  /**
   * @return 父指标编码数组，用于派生指标和复合指标，如果未设置则返回空数组
   */
  default String[] parentMetricCodes() {
    return new String[0];
  }

  /**
   * @return 计算公式，用于复合指标，例如：metric1 / metric2 * 100，如果未设置则返回 null
   */
  default String calculationFormula() {
    return null;
  }

  /**
   * @return 引用的 Table ID，用于原子指标关联数据源，如果未设置则返回 null
   */
  default Long refTableId() {
    return null;
  }

  /**
   * @return 引用的 Catalog 名称（只读，JOIN 查询），如果未设置则返回 null
   */
  default String refCatalogName() {
    return null;
  }

  /**
   * @return 引用的 Schema 名称（只读，JOIN 查询），如果未设置则返回 null
   */
  default String refSchemaName() {
    return null;
  }

  /**
   * @return 引用的 Table 名称（只读，JOIN 查询），如果未设置则返回 null
   */
  default String refTableName() {
    return null;
  }

  /**
   * @return 度量列 ID JSON 数组，格式：[123, 456]，如果未设置则返回 null
   */
  default String measureColumnIds() {
    return null;
  }

  /**
   * @return 过滤列 ID JSON 数组，格式：[789, 012]，如果未设置则返回 null
   */
  default String filterColumnIds() {
    return null;
  }

  /**
   * @return 版本的属性
   */
  default Map<String, String> properties() {
    return Collections.emptyMap();
  }
}
