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

package org.apache.gravitino.listener;

import java.util.Map;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.catalog.DatasetDispatcher;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dataset.MetricChange;
import org.apache.gravitino.dataset.MetricModifier;
import org.apache.gravitino.dataset.MetricVersion;
import org.apache.gravitino.dataset.WordRoot;
import org.apache.gravitino.exceptions.MetricAlreadyExistsException;
import org.apache.gravitino.exceptions.NoSuchMetricException;
import org.apache.gravitino.exceptions.NoSuchMetricVersionException;
import org.apache.gravitino.exceptions.NoSuchSchemaException;
import org.apache.gravitino.listener.api.event.CreateWordRootEvent;
import org.apache.gravitino.listener.api.event.DeleteMetricEvent;
import org.apache.gravitino.listener.api.event.DeleteWordRootEvent;
import org.apache.gravitino.listener.api.event.RegisterMetricEvent;
import org.apache.gravitino.listener.api.info.MetricInfo;
import org.apache.gravitino.listener.api.info.WordRootInfo;
import org.apache.gravitino.utils.PrincipalUtils;

/**
 * {@code DatasetEventDispatcher} is a decorator for {@link DatasetDispatcher} that not only
 * delegates dataset operations to the underlying dispatcher but also dispatches corresponding
 * events to an {@link EventBus} after each operation is completed. This allows for event-driven
 * workflows or monitoring of dataset operations.
 */
public class DatasetEventDispatcher implements DatasetDispatcher {
  private final EventBus eventBus;
  private final DatasetDispatcher dispatcher;

  /**
   * Constructs a {@link DatasetEventDispatcher} with a specified EventBus and {@link
   * DatasetDispatcher}.
   *
   * @param eventBus The EventBus to which events will be dispatched.
   * @param dispatcher The underlying {@link DatasetDispatcher} that will perform the actual
   *     operations.
   */
  public DatasetEventDispatcher(EventBus eventBus, DatasetDispatcher dispatcher) {
    this.eventBus = eventBus;
    this.dispatcher = dispatcher;
  }

  @Override
  public NameIdentifier[] listMetrics(Namespace namespace) throws NoSuchSchemaException {
    return dispatcher.listMetrics(namespace);
  }

  @Override
  public Metric getMetric(NameIdentifier ident) throws NoSuchMetricException {
    return dispatcher.getMetric(ident);
  }

  @Override
  public Metric registerMetric(
      NameIdentifier ident,
      String code,
      Metric.Type type,
      String comment,
      Map<String, String> properties,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula)
      throws NoSuchSchemaException, MetricAlreadyExistsException {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      Metric metric =
          dispatcher.registerMetric(
              ident,
              code,
              type,
              comment,
              properties,
              unit,
              aggregationLogic,
              parentMetricIds,
              calculationFormula);

      // 查询父指标的 codes
      String[] parentMetricCodes =
          org.apache.gravitino.storage.relational.service.MetricMetaService.getInstance()
              .getMetricCodesByIds(parentMetricIds);

      MetricInfo registeredMetricInfo =
          new MetricInfo(
              metric,
              unit,
              aggregationLogic,
              calculationFormula,
              parentMetricIds,
              parentMetricCodes);
      eventBus.dispatchEvent(new RegisterMetricEvent(user, ident, registeredMetricInfo));
      return metric;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public boolean deleteMetric(NameIdentifier ident) {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      boolean isExists = dispatcher.deleteMetric(ident);
      eventBus.dispatchEvent(new DeleteMetricEvent(user, ident, isExists));
      return isExists;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public Metric alterMetric(NameIdentifier ident, MetricChange... changes)
      throws NoSuchMetricException, IllegalArgumentException {
    return dispatcher.alterMetric(ident, changes);
  }

  @Override
  public int[] listMetricVersions(NameIdentifier ident) throws NoSuchMetricException {
    return dispatcher.listMetricVersions(ident);
  }

  @Override
  public MetricVersion[] listMetricVersionInfos(NameIdentifier ident) throws NoSuchMetricException {
    return dispatcher.listMetricVersionInfos(ident);
  }

  @Override
  public MetricVersion getMetricVersion(NameIdentifier ident, int version)
      throws NoSuchMetricVersionException {
    return dispatcher.getMetricVersion(ident, version);
  }

  @Override
  public boolean deleteMetricVersion(NameIdentifier ident, int version) {
    return dispatcher.deleteMetricVersion(ident, version);
  }

  @Override
  public Metric switchMetricVersion(NameIdentifier ident, int targetVersion)
      throws NoSuchMetricException, NoSuchMetricVersionException, IllegalArgumentException {
    return dispatcher.switchMetricVersion(ident, targetVersion);
  }

  @Override
  public NameIdentifier[] listMetricModifiers(Namespace namespace) throws NoSuchSchemaException {
    return dispatcher.listMetricModifiers(namespace);
  }

  @Override
  public MetricModifier getMetricModifier(NameIdentifier ident) {
    return dispatcher.getMetricModifier(ident);
  }

  @Override
  public MetricModifier createMetricModifier(
      NameIdentifier ident, String code, MetricModifier.Type type, String comment)
      throws NoSuchSchemaException {
    return dispatcher.createMetricModifier(ident, code, type, comment);
  }

  @Override
  public boolean deleteMetricModifier(NameIdentifier ident) {
    return dispatcher.deleteMetricModifier(ident);
  }

  @Override
  public NameIdentifier[] listWordRoots(Namespace namespace) throws NoSuchSchemaException {
    return dispatcher.listWordRoots(namespace);
  }

  @Override
  public WordRoot getWordRoot(NameIdentifier ident) {
    return dispatcher.getWordRoot(ident);
  }

  @Override
  public WordRoot createWordRoot(
      NameIdentifier ident, String code, String nameCn, String nameEn, String comment)
      throws NoSuchSchemaException {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      WordRoot wordRoot = dispatcher.createWordRoot(ident, code, nameCn, nameEn, comment);
      WordRootInfo createdWordRootInfo = new WordRootInfo(wordRoot);
      eventBus.dispatchEvent(new CreateWordRootEvent(user, ident, createdWordRootInfo));
      return wordRoot;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public boolean deleteWordRoot(NameIdentifier ident) {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      boolean isExists = dispatcher.deleteWordRoot(ident);
      eventBus.dispatchEvent(new DeleteWordRootEvent(user, ident, isExists));
      return isExists;
    } catch (Exception e) {
      throw e;
    }
  }
}
