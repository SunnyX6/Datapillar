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
   * @return 版本号
   */
  int version();

  /**
   * @return 指标名称快照
   */
  String name();

  /**
   * @return 指标编码快照
   */
  String code();

  /**
   * @return 指标类型快照
   */
  Metric.Type type();

  /**
   * @return 指标注释快照，如果未设置则返回 null
   */
  default String comment() {
    return null;
  }

  /**
   * @return 指标单位，例如：元、个、%，如果未设置则返回 null
   */
  default String unit() {
    return null;
  }

  /**
   * @return 聚合逻辑，例如：SUM、COUNT、AVG、MAX、MIN、DISTINCT_COUNT，如果未设置则返回 null
   */
  default String aggregationLogic() {
    return null;
  }

  /**
   * @return 父指标ID数组，用于派生指标和复合指标，如果未设置则返回空数组
   */
  default Long[] parentMetricIds() {
    return new Long[0];
  }

  /**
   * @return 计算公式，用于复合指标，例如：metric1 / metric2 * 100，如果未设置则返回 null
   */
  default String calculationFormula() {
    return null;
  }

  /**
   * @return 版本的属性
   */
  default Map<String, String> properties() {
    return Collections.emptyMap();
  }
}
