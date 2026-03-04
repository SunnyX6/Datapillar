/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.The ASF licenses this file
 * to you under the Apache License,Version 2.0 (the
 * "License");you may not use this file except in compliance
 * with the License.You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,* software distributed under the License is distributed on an
 * "AS IS" BASIS,WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND,either express or implied.See the License for the
 * specific language governing permissions and limitations
 * under the License.*/
package org.apache.gravitino.dataset;

import java.util.Collections;
import java.util.Map;
import org.apache.gravitino.Auditable;
import org.apache.gravitino.annotation.Evolving;

/**
 * Indicates indicators {@link Metric} A single version snapshot of.An indicator version is a
 * snapshot of the indicator at a certain point in time,Contains all properties and calculation
 * logic of the indicator.
 */
@Evolving
public interface MetricVersion extends Auditable {

  /**
   * @return versionID(auto-increment primary key)
   */
  Long id();

  /**
   * @return version number,from1start
   */
  Integer version();

  /**
   * @return Indicator name snapshot
   */
  String metricName();

  /**
   * @return Indicator encoding snapshot
   */
  String metricCode();

  /**
   * @return Indicator type snapshot
   */
  Metric.Type metricType();

  /**
   * @return Metric annotation snapshot,Returns if not set null
   */
  default String comment() {
    return null;
  }

  /**
   * @return Data type snapshot,Such as STRING,INTEGER,DECIMAL(10,2),Returns if not set null
   */
  default String dataType() {
    return null;
  }

  /**
   * @return Index unit code,Returns if not set null
   */
  default String unit() {
    return null;
  }

  /**
   * @return Indicator unit name,Returns if not set null
   */
  default String unitName() {
    return null;
  }

  /**
   * @return Indicator unit symbol,Such as ¥,$,%,Returns if not set null
   */
  default String unitSymbol() {
    return null;
  }

  /**
   * @return Parent indicator encoding array,Used for derived and composite indicators,Returns an
   *     empty array if not set
   */
  default String[] parentMetricCodes() {
    return new String[0];
  }

  /**
   * @return Calculation formula,for composite indicators,For example:metric1 / metric2 *
   *     100,Returns if not set null
   */
  default String calculationFormula() {
    return null;
  }

  /**
   * @return quoted Table ID,Used to associate data sources with atomic indicators,Returns if not
   *     set null
   */
  default Long refTableId() {
    return null;
  }

  /**
   * @return quoted Catalog Name(read only,JOIN Query),Returns if not set null
   */
  default String refCatalogName() {
    return null;
  }

  /**
   * @return quoted Schema Name(read only,JOIN Query),Returns if not set null
   */
  default String refSchemaName() {
    return null;
  }

  /**
   * @return quoted Table Name(read only,JOIN Query),Returns if not set null
   */
  default String refTableName() {
    return null;
  }

  /**
   * @return measure column ID JSON array,Format:[123,456],Returns if not set null
   */
  default String measureColumnIds() {
    return null;
  }

  /**
   * @return Filter columns ID JSON array,Format:[789,012],Returns if not set null
   */
  default String filterColumnIds() {
    return null;
  }

  /**
   * @return version properties
   */
  default Map<String, String> properties() {
    return Collections.emptyMap();
  }
}
