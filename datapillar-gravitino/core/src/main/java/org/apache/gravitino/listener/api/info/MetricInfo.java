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

package org.apache.gravitino.listener.api.info;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import org.apache.gravitino.Audit;
import org.apache.gravitino.annotation.DeveloperApi;
import org.apache.gravitino.dataset.Metric;

/**
 * MetricInfo exposes metric information for event listener, it's supposed to be read only. Most of
 * the fields are shallow copied internally not deep copies for performance.
 */
@DeveloperApi
public class MetricInfo {
  private final String name;
  private final String code;
  private final Metric.Type type;
  private final Map<String, String> properties;
  private final Optional<String> comment;
  private final Optional<Audit> audit;
  private final Optional<Integer> currentVersion;
  private final Optional<Integer> lastVersion;
  private final Optional<String> unit;
  private final Optional<String> aggregationLogic;
  private final Optional<String> calculationFormula;
  private final Long[] parentMetricIds;
  private final String[] parentMetricCodes;

  /**
   * Constructs a {@link MetricInfo} instance based on a given metric.
   *
   * @param metric the metric to expose information for.
   */
  public MetricInfo(Metric metric) {
    this(
        metric.name(),
        metric.code(),
        metric.type(),
        metric.properties(),
        metric.comment(),
        metric.auditInfo(),
        metric.currentVersion(),
        metric.lastVersion(),
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * Constructs a {@link MetricInfo} instance with version details.
   *
   * @param metric the metric to expose information for.
   * @param unit the unit of the metric.
   * @param aggregationLogic the aggregation logic of the metric.
   * @param calculationFormula the calculation formula of the metric.
   * @param parentMetricIds the parent metric IDs for derived/composite metrics.
   * @param parentMetricCodes the parent metric codes for derived/composite metrics.
   */
  public MetricInfo(
      Metric metric,
      String unit,
      String aggregationLogic,
      String calculationFormula,
      Long[] parentMetricIds,
      String[] parentMetricCodes) {
    this(
        metric.name(),
        metric.code(),
        metric.type(),
        metric.properties(),
        metric.comment(),
        metric.auditInfo(),
        metric.currentVersion(),
        metric.lastVersion(),
        unit,
        aggregationLogic,
        calculationFormula,
        parentMetricIds,
        parentMetricCodes);
  }

  /**
   * Constructs a {@link MetricInfo} instance based on all fields.
   *
   * @param name the name of the metric.
   * @param code the code of the metric.
   * @param type the type of the metric.
   * @param properties the properties of the metric.
   * @param comment the comment of the metric.
   * @param audit the audit information of the metric.
   * @param currentVersion the current version of the metric.
   * @param lastVersion the last version of the metric.
   * @param unit the unit of the metric.
   * @param aggregationLogic the aggregation logic of the metric.
   * @param calculationFormula the calculation formula of the metric.
   * @param parentMetricIds the parent metric IDs for derived/composite metrics.
   * @param parentMetricCodes the parent metric codes for derived/composite metrics.
   */
  public MetricInfo(
      String name,
      String code,
      Metric.Type type,
      Map<String, String> properties,
      String comment,
      Audit audit,
      Integer currentVersion,
      Integer lastVersion,
      String unit,
      String aggregationLogic,
      String calculationFormula,
      Long[] parentMetricIds,
      String[] parentMetricCodes) {
    this.name = name;
    this.code = code;
    this.type = type;
    this.properties = properties == null ? ImmutableMap.of() : ImmutableMap.copyOf(properties);
    this.comment = Optional.ofNullable(comment);
    this.audit = Optional.ofNullable(audit);
    this.currentVersion = Optional.ofNullable(currentVersion);
    this.lastVersion = Optional.ofNullable(lastVersion);
    this.unit = Optional.ofNullable(unit);
    this.aggregationLogic = Optional.ofNullable(aggregationLogic);
    this.calculationFormula = Optional.ofNullable(calculationFormula);
    this.parentMetricIds = parentMetricIds;
    this.parentMetricCodes = parentMetricCodes;
  }

  /**
   * Returns the name of the metric.
   *
   * @return the name of the metric.
   */
  public String name() {
    return name;
  }

  /**
   * Returns the code of the metric.
   *
   * @return the code of the metric.
   */
  public String code() {
    return code;
  }

  /**
   * Returns the type of the metric.
   *
   * @return the type of the metric.
   */
  public Metric.Type metricType() {
    return type;
  }

  /**
   * Returns the properties of the metric.
   *
   * @return the properties of the metric.
   */
  public Map<String, String> properties() {
    return properties;
  }

  /**
   * Returns the comment of the metric.
   *
   * @return the comment of the metric or empty if not set.
   */
  public Optional<String> comment() {
    return comment;
  }

  /**
   * Returns the audit information of the metric.
   *
   * @return the audit information of the metric or empty if not set.
   */
  public Optional<Audit> audit() {
    return audit;
  }

  /**
   * Returns the current version of the metric.
   *
   * @return the current version of the metric, or empty if not set.
   */
  public Optional<Integer> currentVersion() {
    return currentVersion;
  }

  /**
   * Returns the last version of the metric.
   *
   * @return the last version of the metric, or empty if not set.
   */
  public Optional<Integer> lastVersion() {
    return lastVersion;
  }

  /**
   * Returns the unit of the metric.
   *
   * @return the unit of the metric, or empty if not set.
   */
  public Optional<String> unit() {
    return unit;
  }

  /**
   * Returns the aggregation logic of the metric.
   *
   * @return the aggregation logic of the metric, or empty if not set.
   */
  public Optional<String> aggregationLogic() {
    return aggregationLogic;
  }

  /**
   * Returns the calculation formula of the metric.
   *
   * @return the calculation formula of the metric, or empty if not set.
   */
  public Optional<String> calculationFormula() {
    return calculationFormula;
  }

  /**
   * Returns the parent metric IDs for derived/composite metrics.
   *
   * @return the parent metric IDs, or null if not set.
   */
  public Long[] parentMetricIds() {
    return parentMetricIds;
  }

  /**
   * Returns the parent metric codes for derived/composite metrics.
   *
   * @return the parent metric codes, or null if not set.
   */
  public String[] parentMetricCodes() {
    return parentMetricCodes;
  }
}
