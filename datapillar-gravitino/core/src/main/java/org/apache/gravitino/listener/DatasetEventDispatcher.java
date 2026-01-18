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
import org.apache.gravitino.dataset.Unit;
import org.apache.gravitino.dataset.ValueDomain;
import org.apache.gravitino.dataset.WordRoot;
import org.apache.gravitino.exceptions.MetricAlreadyExistsException;
import org.apache.gravitino.exceptions.NoSuchMetricException;
import org.apache.gravitino.exceptions.NoSuchMetricVersionException;
import org.apache.gravitino.exceptions.NoSuchSchemaException;
import org.apache.gravitino.exceptions.UnitAlreadyExistsException;
import org.apache.gravitino.exceptions.ValueDomainAlreadyExistsException;
import org.apache.gravitino.listener.api.event.AlterMetricEvent;
import org.apache.gravitino.listener.api.event.AlterModifierEvent;
import org.apache.gravitino.listener.api.event.AlterUnitEvent;
import org.apache.gravitino.listener.api.event.AlterValueDomainEvent;
import org.apache.gravitino.listener.api.event.AlterWordRootEvent;
import org.apache.gravitino.listener.api.event.CreateModifierEvent;
import org.apache.gravitino.listener.api.event.CreateUnitEvent;
import org.apache.gravitino.listener.api.event.CreateValueDomainEvent;
import org.apache.gravitino.listener.api.event.CreateWordRootEvent;
import org.apache.gravitino.listener.api.event.DropMetricEvent;
import org.apache.gravitino.listener.api.event.DropModifierEvent;
import org.apache.gravitino.listener.api.event.DropUnitEvent;
import org.apache.gravitino.listener.api.event.DropValueDomainEvent;
import org.apache.gravitino.listener.api.event.DropWordRootEvent;
import org.apache.gravitino.listener.api.event.RegisterMetricEvent;
import org.apache.gravitino.listener.api.info.MetricInfo;
import org.apache.gravitino.listener.api.info.ModifierInfo;
import org.apache.gravitino.listener.api.info.UnitInfo;
import org.apache.gravitino.listener.api.info.ValueDomainInfo;
import org.apache.gravitino.listener.api.info.WordRootInfo;
import org.apache.gravitino.pagination.PagedResult;
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
  public PagedResult<Metric> listMetrics(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    return dispatcher.listMetrics(namespace, offset, limit);
  }

  @Override
  public Metric getMetric(NameIdentifier ident) throws NoSuchMetricException {
    return dispatcher.getMetric(ident);
  }

  @Override
  public Metric registerMetric(
      NameIdentifier ident,
      String name,
      String code,
      Metric.Type type,
      String dataType,
      String comment,
      Map<String, String> properties,
      String unit,
      String[] parentMetricCodes,
      String calculationFormula,
      Long refTableId,
      String refCatalogName,
      String refSchemaName,
      String refTableName,
      String measureColumnIds,
      String filterColumnIds)
      throws NoSuchSchemaException, MetricAlreadyExistsException {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      Metric metric =
          dispatcher.registerMetric(
              ident,
              name,
              code,
              type,
              dataType,
              comment,
              properties,
              unit,
              parentMetricCodes,
              calculationFormula,
              refTableId,
              refCatalogName,
              refSchemaName,
              refTableName,
              measureColumnIds,
              filterColumnIds);

      MetricInfo registeredMetricInfo =
          new MetricInfo(
              metric,
              unit,
              calculationFormula,
              parentMetricCodes,
              refCatalogName,
              refSchemaName,
              refTableName,
              measureColumnIds,
              filterColumnIds);
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
      eventBus.dispatchEvent(new DropMetricEvent(user, ident, isExists));
      return isExists;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public Metric alterMetric(NameIdentifier ident, MetricChange... changes)
      throws NoSuchMetricException, IllegalArgumentException {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      Metric metric = dispatcher.alterMetric(ident, changes);

      // 查询当前版本的详细信息，确保发送完整的数据到 Neo4j
      MetricVersion currentVersionInfo =
          dispatcher.getMetricVersion(ident, metric.currentVersion());

      MetricInfo updatedMetricInfo =
          new MetricInfo(
              metric,
              currentVersionInfo.unit(),
              currentVersionInfo.calculationFormula(),
              currentVersionInfo.parentMetricCodes(),
              currentVersionInfo.refCatalogName(),
              currentVersionInfo.refSchemaName(),
              currentVersionInfo.refTableName(),
              currentVersionInfo.measureColumnIds(),
              currentVersionInfo.filterColumnIds());

      eventBus.dispatchEvent(new AlterMetricEvent(user, ident, updatedMetricInfo));

      return metric;
    } catch (Exception e) {
      throw e;
    }
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
  public MetricVersion switchMetricVersion(NameIdentifier ident, int targetVersion)
      throws NoSuchMetricException, NoSuchMetricVersionException, IllegalArgumentException {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      MetricVersion metricVersion = dispatcher.switchMetricVersion(ident, targetVersion);

      // 获取主表信息用于事件
      Metric metric = dispatcher.getMetric(ident);

      MetricInfo updatedMetricInfo =
          new MetricInfo(
              metric,
              metricVersion.unit(),
              metricVersion.calculationFormula(),
              metricVersion.parentMetricCodes(),
              metricVersion.refCatalogName(),
              metricVersion.refSchemaName(),
              metricVersion.refTableName(),
              metricVersion.measureColumnIds(),
              metricVersion.filterColumnIds());

      eventBus.dispatchEvent(new AlterMetricEvent(user, ident, updatedMetricInfo));

      return metricVersion;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public MetricVersion alterMetricVersion(
      NameIdentifier ident,
      int version,
      String metricName,
      String metricCode,
      String metricType,
      String dataType,
      String comment,
      String unit,
      String unitName,
      String[] parentMetricCodes,
      String calculationFormula,
      Long refTableId,
      String measureColumnIds,
      String filterColumnIds)
      throws NoSuchMetricVersionException {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      MetricVersion metricVersion =
          dispatcher.alterMetricVersion(
              ident,
              version,
              metricName,
              metricCode,
              metricType,
              dataType,
              comment,
              unit,
              unitName,
              parentMetricCodes,
              calculationFormula,
              refTableId,
              measureColumnIds,
              filterColumnIds);

      // 获取最新的 Metric 信息
      Metric metric = dispatcher.getMetric(ident);

      MetricInfo updatedMetricInfo =
          new MetricInfo(
              metric,
              unit,
              calculationFormula,
              parentMetricCodes,
              metricVersion.refCatalogName(),
              metricVersion.refSchemaName(),
              metricVersion.refTableName(),
              metricVersion.measureColumnIds(),
              metricVersion.filterColumnIds());

      eventBus.dispatchEvent(new AlterMetricEvent(user, ident, updatedMetricInfo));

      return metricVersion;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public PagedResult<MetricModifier> listMetricModifiers(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    return dispatcher.listMetricModifiers(namespace, offset, limit);
  }

  @Override
  public MetricModifier getMetricModifier(NameIdentifier ident) {
    return dispatcher.getMetricModifier(ident);
  }

  @Override
  public MetricModifier createMetricModifier(
      NameIdentifier ident, String code, String comment, String modifierType)
      throws NoSuchSchemaException {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      MetricModifier modifier = dispatcher.createMetricModifier(ident, code, comment, modifierType);
      ModifierInfo createdModifierInfo = new ModifierInfo(modifier);
      eventBus.dispatchEvent(new CreateModifierEvent(user, ident, createdModifierInfo));
      return modifier;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public boolean deleteMetricModifier(NameIdentifier ident) {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      boolean isExists = dispatcher.deleteMetricModifier(ident);
      eventBus.dispatchEvent(new DropModifierEvent(user, ident, isExists));
      return isExists;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public MetricModifier alterMetricModifier(NameIdentifier ident, String name, String comment) {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      MetricModifier modifier = dispatcher.alterMetricModifier(ident, name, comment);
      ModifierInfo updatedModifierInfo = new ModifierInfo(modifier);
      eventBus.dispatchEvent(new AlterModifierEvent(user, ident, updatedModifierInfo));
      return modifier;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public PagedResult<WordRoot> listWordRoots(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    return dispatcher.listWordRoots(namespace, offset, limit);
  }

  @Override
  public WordRoot getWordRoot(NameIdentifier ident) {
    return dispatcher.getWordRoot(ident);
  }

  @Override
  public WordRoot createWordRoot(
      NameIdentifier ident, String code, String name, String dataType, String comment)
      throws NoSuchSchemaException {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      WordRoot wordRoot = dispatcher.createWordRoot(ident, code, name, dataType, comment);
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
      eventBus.dispatchEvent(new DropWordRootEvent(user, ident, isExists));
      return isExists;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public WordRoot alterWordRoot(
      NameIdentifier ident, String name, String dataType, String comment) {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      WordRoot wordRoot = dispatcher.alterWordRoot(ident, name, dataType, comment);
      WordRootInfo updatedWordRootInfo = new WordRootInfo(wordRoot);
      eventBus.dispatchEvent(new AlterWordRootEvent(user, ident, updatedWordRootInfo));
      return wordRoot;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public PagedResult<Unit> listUnits(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    return dispatcher.listUnits(namespace, offset, limit);
  }

  @Override
  public Unit getUnit(NameIdentifier ident) {
    return dispatcher.getUnit(ident);
  }

  @Override
  public Unit createUnit(
      NameIdentifier ident, String code, String name, String symbol, String comment)
      throws NoSuchSchemaException, UnitAlreadyExistsException {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      Unit unit = dispatcher.createUnit(ident, code, name, symbol, comment);
      UnitInfo createdUnitInfo = new UnitInfo(unit);
      eventBus.dispatchEvent(new CreateUnitEvent(user, ident, createdUnitInfo));
      return unit;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public boolean deleteUnit(NameIdentifier ident) {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      boolean isExists = dispatcher.deleteUnit(ident);
      eventBus.dispatchEvent(new DropUnitEvent(user, ident, isExists));
      return isExists;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public Unit alterUnit(NameIdentifier ident, String name, String symbol, String comment) {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      Unit unit = dispatcher.alterUnit(ident, name, symbol, comment);
      UnitInfo updatedUnitInfo = new UnitInfo(unit);
      eventBus.dispatchEvent(new AlterUnitEvent(user, ident, updatedUnitInfo));
      return unit;
    } catch (Exception e) {
      throw e;
    }
  }

  // ==================== ValueDomain 值域相关方法 ====================

  @Override
  public PagedResult<ValueDomain> listValueDomains(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    return dispatcher.listValueDomains(namespace, offset, limit);
  }

  @Override
  public ValueDomain getValueDomain(NameIdentifier ident) {
    return dispatcher.getValueDomain(ident);
  }

  @Override
  public ValueDomain createValueDomain(
      NameIdentifier ident,
      String domainCode,
      String domainName,
      ValueDomain.Type domainType,
      ValueDomain.Level domainLevel,
      java.util.List<ValueDomain.Item> items,
      String comment,
      String dataType)
      throws NoSuchSchemaException, ValueDomainAlreadyExistsException {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      ValueDomain valueDomain =
          dispatcher.createValueDomain(
              ident, domainCode, domainName, domainType, domainLevel, items, comment, dataType);
      ValueDomainInfo createdValueDomainInfo = new ValueDomainInfo(valueDomain);
      eventBus.dispatchEvent(new CreateValueDomainEvent(user, ident, createdValueDomainInfo));
      return valueDomain;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public boolean deleteValueDomain(NameIdentifier ident) {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      boolean isExists = dispatcher.deleteValueDomain(ident);
      eventBus.dispatchEvent(new DropValueDomainEvent(user, ident, isExists));
      return isExists;
    } catch (Exception e) {
      throw e;
    }
  }

  @Override
  public ValueDomain alterValueDomain(
      NameIdentifier ident,
      String domainName,
      ValueDomain.Level domainLevel,
      java.util.List<ValueDomain.Item> items,
      String comment,
      String dataType) {
    String user = PrincipalUtils.getCurrentUserName();

    try {
      ValueDomain valueDomain =
          dispatcher.alterValueDomain(ident, domainName, domainLevel, items, comment, dataType);
      ValueDomainInfo updatedValueDomainInfo = new ValueDomainInfo(valueDomain);
      eventBus.dispatchEvent(new AlterValueDomainEvent(user, ident, updatedValueDomainInfo));
      return valueDomain;
    } catch (Exception e) {
      throw e;
    }
  }
}
