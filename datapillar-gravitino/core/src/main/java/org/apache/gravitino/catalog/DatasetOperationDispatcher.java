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
import org.apache.gravitino.dataset.WordRoot;
import org.apache.gravitino.exceptions.MetricAlreadyExistsException;
import org.apache.gravitino.exceptions.NoSuchMetricException;
import org.apache.gravitino.exceptions.NoSuchMetricVersionException;
import org.apache.gravitino.exceptions.NoSuchSchemaException;
import org.apache.gravitino.lock.LockType;
import org.apache.gravitino.lock.TreeLockUtils;
import org.apache.gravitino.storage.IdGenerator;

/** DatasetOperationDispatcher 负责调度所有数据集相关的操作 */
public class DatasetOperationDispatcher extends OperationDispatcher implements DatasetDispatcher {

  public DatasetOperationDispatcher(
      CatalogManager catalogManager, EntityStore store, IdGenerator idGenerator) {
    super(catalogManager, store, idGenerator);
  }

  @Override
  public NameIdentifier[] listMetrics(Namespace namespace) throws NoSuchSchemaException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(namespace.levels()),
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(NameIdentifier.of(namespace.levels())),
                c -> c.doWithDatasetOps(m -> m.listMetrics(namespace)),
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
      String code,
      Metric.Type type,
      String comment,
      Map<String, String> properties,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula)
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
                                code,
                                type,
                                comment,
                                finalProperties,
                                unit,
                                aggregationLogic,
                                parentMetricIds,
                                calculationFormula)),
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
  public Metric switchMetricVersion(NameIdentifier ident, int targetVersion)
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
  public NameIdentifier[] listMetricModifiers(Namespace namespace) throws NoSuchSchemaException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(namespace.levels()),
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(NameIdentifier.of(namespace.levels())),
                c -> c.doWithDatasetOps(m -> m.listMetricModifiers(namespace)),
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
      NameIdentifier ident, String code, MetricModifier.Type type, String comment)
      throws NoSuchSchemaException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(ident.namespace().levels()),
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c -> c.doWithDatasetOps(m -> m.createMetricModifier(ident, code, type, comment)),
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
  public NameIdentifier[] listWordRoots(Namespace namespace) throws NoSuchSchemaException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(namespace.levels()),
        LockType.READ,
        () ->
            doWithCatalog(
                getCatalogIdentifier(NameIdentifier.of(namespace.levels())),
                c -> c.doWithDatasetOps(m -> m.listWordRoots(namespace)),
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
      NameIdentifier ident, String code, String nameCn, String nameEn, String comment)
      throws NoSuchSchemaException {
    return TreeLockUtils.doWithTreeLock(
        NameIdentifier.of(ident.namespace().levels()),
        LockType.WRITE,
        () ->
            doWithCatalog(
                getCatalogIdentifier(ident),
                c ->
                    c.doWithDatasetOps(m -> m.createWordRoot(ident, code, nameCn, nameEn, comment)),
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
