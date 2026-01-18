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
package org.apache.gravitino.catalog;

import static org.apache.gravitino.catalog.PropertiesMetadataHelpers.validatePropertyForCreate;
import static org.apache.gravitino.utils.NameIdentifierUtil.getCatalogIdentifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.apache.gravitino.EntityStore;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.StringIdentifier;
import org.apache.gravitino.connector.HasPropertyMetadata;
import org.apache.gravitino.connector.PropertiesMetadata;
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
import org.apache.gravitino.lock.LockType;
import org.apache.gravitino.lock.TreeLockUtils;
import org.apache.gravitino.pagination.PagedResult;
import org.apache.gravitino.storage.IdGenerator;

/** DatasetOperationDispatcher 负责调度所有数据集相关的操作 */
public class DatasetOperationDispatcher extends OperationDispatcher implements DatasetDispatcher {

  public DatasetOperationDispatcher(
      CatalogManager catalogManager, EntityStore store, IdGenerator idGenerator) {
    super(catalogManager, store, idGenerator);
  }

  @Override
  public PagedResult<Metric> listMetrics(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(namespace.levels()),
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(NameIdentifier.of(namespace.levels())),
                c -> c.doWithDatasetOps(m -> m.listMetrics(namespace, offset, limit)),
                NoSuchSchemaException.class));
  }

  @Override
  public Metric getMetric(NameIdentifier ident) throws NoSuchMetricException {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.getMetric(ident)),
                NoSuchMetricException.class));
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
    NameIdentifier catalogIdent = getCatalogIdentifier(ident);
    // 确保 properties 不为 null，以便生成 StringIdentifier
    Map<String, String> inputProperties = properties != null ? properties : new HashMap<>();
    Map<String, String> updatedProperties =
        checkAndUpdateProperties(catalogIdent, inputProperties, p -> p.metricPropertiesMetadata());
    final Map<String, String> finalProperties = updatedProperties;

    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(ident.namespace().levels()),
        LockType.WRITE,
        () ->
            doWithCatalog(
                catalogIdent,
                c ->
                    c.doWithDatasetOps(
                        m ->
                            m.registerMetric(
                                ident,
                                name,
                                code,
                                type,
                                dataType,
                                comment,
                                finalProperties,
                                unit,
                                parentMetricCodes,
                                calculationFormula,
                                refTableId,
                                refCatalogName,
                                refSchemaName,
                                refTableName,
                                measureColumnIds,
                                filterColumnIds)),
                NoSuchSchemaException.class,
                MetricAlreadyExistsException.class));
  }

  @Override
  public boolean deleteMetric(NameIdentifier ident) {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(ident.namespace().levels()),
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.deleteMetric(ident)),
                RuntimeException.class));
  }

  @Override
  public Metric alterMetric(NameIdentifier ident, MetricChange... changes)
      throws NoSuchMetricException, IllegalArgumentException {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.alterMetric(ident, changes)),
                NoSuchMetricException.class,
                IllegalArgumentException.class));
  }

  @Override
  public int[] listMetricVersions(NameIdentifier ident) throws NoSuchMetricException {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.listMetricVersions(ident)),
                NoSuchMetricException.class));
  }

  @Override
  public MetricVersion[] listMetricVersionInfos(NameIdentifier ident) throws NoSuchMetricException {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.listMetricVersionInfos(ident)),
                NoSuchMetricException.class));
  }

  @Override
  public MetricVersion getMetricVersion(NameIdentifier ident, int version)
      throws NoSuchMetricVersionException {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.getMetricVersion(ident, version)),
                NoSuchMetricVersionException.class));
  }

  @Override
  public boolean deleteMetricVersion(NameIdentifier ident, int version) {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.deleteMetricVersion(ident, version)),
                RuntimeException.class));
  }

  @Override
  public MetricVersion switchMetricVersion(NameIdentifier ident, int targetVersion)
      throws NoSuchMetricException, NoSuchMetricVersionException, IllegalArgumentException {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.switchMetricVersion(ident, targetVersion)),
                NoSuchMetricException.class,
                NoSuchMetricVersionException.class));
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
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c ->
                    c.doWithDatasetOps(
                        m ->
                            m.alterMetricVersion(
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
                                filterColumnIds)),
                NoSuchMetricVersionException.class));
  }

  @Override
  public PagedResult<MetricModifier> listMetricModifiers(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(namespace.levels()),
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(NameIdentifier.of(namespace.levels())),
                c -> c.doWithDatasetOps(m -> m.listMetricModifiers(namespace, offset, limit)),
                NoSuchSchemaException.class));
  }

  @Override
  public MetricModifier getMetricModifier(NameIdentifier ident) {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.getMetricModifier(ident)),
                RuntimeException.class));
  }

  @Override
  public MetricModifier createMetricModifier(
      NameIdentifier ident, String code, String comment, String modifierType)
      throws NoSuchSchemaException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(ident.namespace().levels()),
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c ->
                    c.doWithDatasetOps(
                        m -> m.createMetricModifier(ident, code, comment, modifierType)),
                NoSuchSchemaException.class));
  }

  @Override
  public boolean deleteMetricModifier(NameIdentifier ident) {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(ident.namespace().levels()),
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.deleteMetricModifier(ident)),
                RuntimeException.class));
  }

  @Override
  public MetricModifier alterMetricModifier(NameIdentifier ident, String name, String comment) {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.alterMetricModifier(ident, name, comment)),
                RuntimeException.class));
  }

  @Override
  public PagedResult<WordRoot> listWordRoots(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(namespace.levels()),
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(NameIdentifier.of(namespace.levels())),
                c -> c.doWithDatasetOps(m -> m.listWordRoots(namespace, offset, limit)),
                NoSuchSchemaException.class));
  }

  @Override
  public WordRoot getWordRoot(NameIdentifier ident) {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.getWordRoot(ident)),
                RuntimeException.class));
  }

  @Override
  public WordRoot createWordRoot(
      NameIdentifier ident, String code, String name, String dataType, String comment)
      throws NoSuchSchemaException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(ident.namespace().levels()),
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c ->
                    c.doWithDatasetOps(m -> m.createWordRoot(ident, code, name, dataType, comment)),
                NoSuchSchemaException.class));
  }

  @Override
  public boolean deleteWordRoot(NameIdentifier ident) {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(ident.namespace().levels()),
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.deleteWordRoot(ident)),
                RuntimeException.class));
  }

  @Override
  public WordRoot alterWordRoot(
      NameIdentifier ident, String name, String dataType, String comment) {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.alterWordRoot(ident, name, dataType, comment)),
                RuntimeException.class));
  }

  @Override
  public PagedResult<Unit> listUnits(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(namespace.levels()),
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(NameIdentifier.of(namespace.levels())),
                c -> c.doWithDatasetOps(m -> m.listUnits(namespace, offset, limit)),
                NoSuchSchemaException.class));
  }

  @Override
  public Unit getUnit(NameIdentifier ident) {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.getUnit(ident)),
                RuntimeException.class));
  }

  @Override
  public Unit createUnit(
      NameIdentifier ident, String code, String name, String symbol, String comment)
      throws NoSuchSchemaException, UnitAlreadyExistsException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(ident.namespace().levels()),
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.createUnit(ident, code, name, symbol, comment)),
                NoSuchSchemaException.class));
  }

  @Override
  public boolean deleteUnit(NameIdentifier ident) {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(ident.namespace().levels()),
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.deleteUnit(ident)),
                RuntimeException.class));
  }

  @Override
  public Unit alterUnit(NameIdentifier ident, String name, String symbol, String comment) {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.alterUnit(ident, name, symbol, comment)),
                RuntimeException.class));
  }

  // ==================== ValueDomain 值域相关方法 ====================

  @Override
  public PagedResult<ValueDomain> listValueDomains(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(namespace.levels()),
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(NameIdentifier.of(namespace.levels())),
                c -> c.doWithDatasetOps(m -> m.listValueDomains(namespace, offset, limit)),
                NoSuchSchemaException.class));
  }

  @Override
  public ValueDomain getValueDomain(NameIdentifier ident) {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.getValueDomain(ident)),
                RuntimeException.class));
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
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(ident.namespace().levels()),
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c ->
                    c.doWithDatasetOps(
                        m ->
                            m.createValueDomain(
                                ident,
                                domainCode,
                                domainName,
                                domainType,
                                domainLevel,
                                items,
                                comment,
                                dataType)),
                NoSuchSchemaException.class));
  }

  @Override
  public boolean deleteValueDomain(NameIdentifier ident) {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(ident.namespace().levels()),
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.deleteValueDomain(ident)),
                RuntimeException.class));
  }

  @Override
  public ValueDomain alterValueDomain(
      NameIdentifier ident,
      String domainName,
      ValueDomain.Level domainLevel,
      java.util.List<ValueDomain.Item> items,
      String comment,
      String dataType) {
    return TreeLockUtils.doWithTreeLock(
        ident,
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c ->
                    c.doWithDatasetOps(
                        m ->
                            m.alterValueDomain(
                                ident, domainName, domainLevel, items, comment, dataType)),
                RuntimeException.class));
  }

  private Map<String, String> checkAndUpdateProperties(
      NameIdentifier catalogIdent,
      Map<String, String> properties,
      Function<HasPropertyMetadata, PropertiesMetadata> propertiesMetadataProvider) {
    TreeLockUtils.doWithTreeLock(
        catalogIdent,
        LockType.READ,
        () ->
            doWithCatalog(
                catalogIdent,
                c ->
                    c.doWithPropertiesMeta(
                        p -> {
                          validatePropertyForCreate(
                              propertiesMetadataProvider.apply(p), properties);
                          return null;
                        }),
                IllegalArgumentException.class));

    long uid = idGenerator.nextId();
    StringIdentifier stringId = StringIdentifier.fromId(uid);
    return StringIdentifier.newPropertiesWithId(stringId, properties);
  }
}
