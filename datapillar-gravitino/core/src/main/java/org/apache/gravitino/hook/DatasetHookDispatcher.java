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
package org.apache.gravitino.hook;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.apache.gravitino.Entity;
import org.apache.gravitino.GravitinoEnv;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.authorization.AuthorizationUtils;
import org.apache.gravitino.authorization.Owner;
import org.apache.gravitino.authorization.OwnerDispatcher;
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
import org.apache.gravitino.pagination.PagedResult;
import org.apache.gravitino.utils.NameIdentifierUtil;
import org.apache.gravitino.utils.PrincipalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code DatasetHookDispatcher} is a decorator for {@link DatasetDispatcher} that executes the
 * dataset owner and authorization side effects before or after the underlying operation.
 */
public class DatasetHookDispatcher implements DatasetDispatcher {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetHookDispatcher.class);

  private final DatasetDispatcher dispatcher;

  public DatasetHookDispatcher(DatasetDispatcher dispatcher) {
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
    checkCurrentUser(ident);
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
    return bindOwnerWithRollback(
        ident, Entity.EntityType.METRIC, metric, () -> dispatcher.deleteMetric(ident));
  }

  @Override
  public boolean deleteMetric(NameIdentifier ident) {
    return deleteWithPrivilegeCleanup(
        ident, Entity.EntityType.METRIC, () -> dispatcher.deleteMetric(ident));
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
  public MetricVersion switchMetricVersion(NameIdentifier ident, int targetVersion)
      throws NoSuchMetricException, NoSuchMetricVersionException, IllegalArgumentException {
    return dispatcher.switchMetricVersion(ident, targetVersion);
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
    return dispatcher.alterMetricVersion(
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
    checkCurrentUser(ident);
    MetricModifier modifier = dispatcher.createMetricModifier(ident, code, comment, modifierType);
    return bindOwnerWithRollback(
        ident, Entity.EntityType.MODIFIER, modifier, () -> dispatcher.deleteMetricModifier(ident));
  }

  @Override
  public boolean deleteMetricModifier(NameIdentifier ident) {
    return deleteWithPrivilegeCleanup(
        ident, Entity.EntityType.MODIFIER, () -> dispatcher.deleteMetricModifier(ident));
  }

  @Override
  public MetricModifier alterMetricModifier(NameIdentifier ident, String name, String comment) {
    return dispatcher.alterMetricModifier(ident, name, comment);
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
    checkCurrentUser(ident);
    WordRoot wordRoot = dispatcher.createWordRoot(ident, code, name, dataType, comment);
    return bindOwnerWithRollback(
        ident, Entity.EntityType.WORDROOT, wordRoot, () -> dispatcher.deleteWordRoot(ident));
  }

  @Override
  public boolean deleteWordRoot(NameIdentifier ident) {
    return deleteWithPrivilegeCleanup(
        ident, Entity.EntityType.WORDROOT, () -> dispatcher.deleteWordRoot(ident));
  }

  @Override
  public WordRoot alterWordRoot(
      NameIdentifier ident, String name, String dataType, String comment) {
    return dispatcher.alterWordRoot(ident, name, dataType, comment);
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
    checkCurrentUser(ident);
    Unit unit = dispatcher.createUnit(ident, code, name, symbol, comment);
    return bindOwnerWithRollback(
        ident, Entity.EntityType.UNIT, unit, () -> dispatcher.deleteUnit(ident));
  }

  @Override
  public boolean deleteUnit(NameIdentifier ident) {
    return deleteWithPrivilegeCleanup(
        ident, Entity.EntityType.UNIT, () -> dispatcher.deleteUnit(ident));
  }

  @Override
  public Unit alterUnit(NameIdentifier ident, String name, String symbol, String comment) {
    return dispatcher.alterUnit(ident, name, symbol, comment);
  }

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
    checkCurrentUser(ident);
    ValueDomain valueDomain =
        dispatcher.createValueDomain(
            ident, domainCode, domainName, domainType, domainLevel, items, comment, dataType);
    return bindOwnerWithRollback(
        ident,
        Entity.EntityType.VALUE_DOMAIN,
        valueDomain,
        () -> dispatcher.deleteValueDomain(ident));
  }

  @Override
  public boolean deleteValueDomain(NameIdentifier ident) {
    return deleteWithPrivilegeCleanup(
        ident, Entity.EntityType.VALUE_DOMAIN, () -> dispatcher.deleteValueDomain(ident));
  }

  @Override
  public ValueDomain alterValueDomain(
      NameIdentifier ident,
      String domainName,
      ValueDomain.Level domainLevel,
      java.util.List<ValueDomain.Item> items,
      String comment,
      String dataType) {
    return dispatcher.alterValueDomain(ident, domainName, domainLevel, items, comment, dataType);
  }

  private void checkCurrentUser(NameIdentifier ident) {
    AuthorizationUtils.checkCurrentUser(
        ident.namespace().level(0), PrincipalUtils.getCurrentUserName());
  }

  private boolean deleteWithPrivilegeCleanup(
      NameIdentifier ident, Entity.EntityType entityType, BooleanSupplier deleteAction) {
    List<String> locations = AuthorizationUtils.getMetadataObjectLocation(ident, entityType);
    boolean deleted = deleteAction.getAsBoolean();
    AuthorizationUtils.authorizationPluginRemovePrivileges(ident, entityType, locations);
    return deleted;
  }

  private <T> T bindOwnerWithRollback(
      NameIdentifier ident,
      Entity.EntityType entityType,
      T createdObject,
      Runnable rollbackAction) {
    OwnerDispatcher ownerDispatcher = GravitinoEnv.getInstance().ownerDispatcher();
    if (ownerDispatcher == null) {
      return createdObject;
    }

    try {
      ownerDispatcher.setOwner(
          ident.namespace().level(0),
          NameIdentifierUtil.toMetadataObject(ident, entityType),
          PrincipalUtils.getCurrentUserName(),
          Owner.Type.USER);
      return createdObject;
    } catch (Exception exception) {
      LOG.warn(
          "Failed to execute dataset owner post hook, rolling back dataset object {}",
          ident,
          exception);
      rollbackAction.run();
      throw exception;
    }
  }
}
