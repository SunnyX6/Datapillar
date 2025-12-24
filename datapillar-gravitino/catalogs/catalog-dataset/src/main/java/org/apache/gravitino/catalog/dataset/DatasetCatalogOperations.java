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
package org.apache.gravitino.catalog.dataset;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.gravitino.Catalog;
import org.apache.gravitino.Entity;
import org.apache.gravitino.EntityAlreadyExistsException;
import org.apache.gravitino.EntityStore;
import org.apache.gravitino.GravitinoEnv;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.StringIdentifier;
import org.apache.gravitino.catalog.ManagedSchemaOperations;
import org.apache.gravitino.connector.CatalogInfo;
import org.apache.gravitino.connector.CatalogOperations;
import org.apache.gravitino.connector.HasPropertyMetadata;
import org.apache.gravitino.dataset.DatasetCatalog;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.dataset.MetricChange;
import org.apache.gravitino.dataset.MetricVersion;
import org.apache.gravitino.exceptions.MetricAlreadyExistsException;
import org.apache.gravitino.exceptions.NoSuchEntityException;
import org.apache.gravitino.exceptions.NoSuchMetricException;
import org.apache.gravitino.exceptions.NoSuchMetricVersionException;
import org.apache.gravitino.exceptions.NoSuchSchemaException;
import org.apache.gravitino.meta.AuditInfo;
import org.apache.gravitino.meta.MetricEntity;
import org.apache.gravitino.meta.MetricModifierEntity;
import org.apache.gravitino.meta.MetricVersionEntity;
import org.apache.gravitino.meta.UnitEntity;
import org.apache.gravitino.meta.ValueDomainEntity;
import org.apache.gravitino.meta.WordRootEntity;
import org.apache.gravitino.pagination.PagedResult;
import org.apache.gravitino.storage.relational.service.MetricMetaService;
import org.apache.gravitino.storage.relational.service.MetricModifierMetaService;
import org.apache.gravitino.storage.relational.service.UnitMetaService;
import org.apache.gravitino.storage.relational.service.ValueDomainMetaService;
import org.apache.gravitino.storage.relational.service.WordRootMetaService;
import org.apache.gravitino.tag.TagDispatcher;
import org.apache.gravitino.utils.NameIdentifierUtil;
import org.apache.gravitino.utils.NamespaceUtil;
import org.apache.gravitino.utils.PrincipalUtils;

/** Dataset Catalog 操作类，实现所有 Metric 和 WordRoot 相关的 CRUD 操作 */
public class DatasetCatalogOperations extends ManagedSchemaOperations
    implements CatalogOperations, DatasetCatalog {

  private static final int INIT_VERSION = 1;

  private final EntityStore store;

  public DatasetCatalogOperations(EntityStore store) {
    this.store = store;
  }

  @Override
  public void initialize(
      Map<String, String> config, CatalogInfo info, HasPropertyMetadata propertiesMetadata)
      throws RuntimeException {}

  @Override
  public void close() throws IOException {}

  @Override
  public void testConnection(
      NameIdentifier catalogIdent,
      Catalog.Type type,
      String provider,
      String comment,
      Map<String, String> properties) {
    // Dataset catalog 不需要连接测试
  }

  @Override
  protected EntityStore store() {
    return store;
  }

  @Override
  public PagedResult<NameIdentifier> listMetrics(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    NamespaceUtil.checkMetric(namespace);

    try {
      List<MetricEntity> metrics =
          MetricMetaService.getInstance()
              .listMetricsByNamespaceWithPagination(namespace, offset, limit);
      long total = MetricMetaService.getInstance().countMetricsByNamespace(namespace);

      List<NameIdentifier> idents =
          metrics.stream()
              .map(m -> NameIdentifier.of(namespace, m.code()))
              .collect(Collectors.toList());

      return new PagedResult<>(idents, total, offset, limit);

    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", namespace);
    }
  }

  @Override
  public Metric getMetric(NameIdentifier ident) throws NoSuchMetricException {
    NameIdentifierUtil.checkMetric(ident);

    try {
      MetricEntity metric = store.get(ident, Entity.EntityType.METRIC, MetricEntity.class);
      return toMetricImpl(metric);

    } catch (NoSuchEntityException e) {
      throw new NoSuchMetricException(e, "Metric %s does not exist", ident);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to get metric " + ident, ioe);
    }
  }

  @Override
  public boolean metricExists(NameIdentifier ident) {
    NameIdentifierUtil.checkMetric(ident);

    try {
      return store.exists(ident, Entity.EntityType.METRIC);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to check if metric exists " + ident, ioe);
    }
  }

  @Override
  public Metric registerMetric(
      NameIdentifier ident,
      String code,
      Metric.Type type,
      String dataType,
      String comment,
      Map<String, String> properties,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula,
      String refCatalogName,
      String refSchemaName,
      String refTableName,
      String measureColumns,
      String filterColumns)
      throws MetricAlreadyExistsException {
    NameIdentifierUtil.checkMetric(ident);

    StringIdentifier stringId = StringIdentifier.fromProperties(properties);
    Preconditions.checkArgument(stringId != null, "Property string identifier should not be null");

    MetricEntity metric =
        MetricEntity.builder()
            .withId(stringId.id())
            .withName(ident.name())
            .withNamespace(ident.namespace())
            .withCode(code)
            .withType(type)
            .withDataType(dataType)
            .withComment(comment)
            .withProperties(properties)
            .withCurrentVersion(INIT_VERSION)
            .withLastVersion(INIT_VERSION)
            .withAuditInfo(
                AuditInfo.builder()
                    .withCreator(PrincipalUtils.getCurrentPrincipal().getName())
                    .withCreateTime(Instant.now())
                    .build())
            .build();

    try {
      // 使用MetricMetaService插入指标，并传递版本相关字段
      MetricMetaService.getInstance()
          .insertMetricWithVersion(
              metric,
              unit,
              aggregationLogic,
              parentMetricIds,
              calculationFormula,
              refCatalogName,
              refSchemaName,
              refTableName,
              measureColumns,
              filterColumns);
    } catch (IOException e) {
      throw new RuntimeException("Failed to register metric " + ident, e);
    } catch (EntityAlreadyExistsException e) {
      throw new MetricAlreadyExistsException(e, "Metric %s already exists", ident);
    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", ident.namespace());
    }

    return toMetricImpl(metric);
  }

  @Override
  public boolean deleteMetric(NameIdentifier ident) {
    NameIdentifierUtil.checkMetric(ident);

    try {
      return store.delete(ident, Entity.EntityType.METRIC);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to delete metric " + ident, ioe);
    }
  }

  @Override
  public Metric alterMetric(NameIdentifier ident, MetricChange... changes)
      throws NoSuchMetricException, IllegalArgumentException {
    NameIdentifierUtil.checkMetric(ident);

    try {
      MetricEntity updatedEntity =
          store.update(
              ident,
              MetricEntity.class,
              Entity.EntityType.METRIC,
              entity -> {
                MetricEntity.Builder builder =
                    MetricEntity.builder()
                        .withId(entity.id())
                        .withName(entity.name())
                        .withNamespace(entity.namespace())
                        .withCode(entity.code())
                        .withType(entity.metricType())
                        .withDataType(entity.dataType())
                        .withComment(entity.comment())
                        .withCurrentVersion(entity.currentVersion())
                        .withLastVersion(entity.lastVersion())
                        .withAuditInfo(
                            AuditInfo.builder()
                                .withCreator(entity.auditInfo().creator())
                                .withCreateTime(entity.auditInfo().createTime())
                                .withLastModifier(PrincipalUtils.getCurrentPrincipal().getName())
                                .withLastModifiedTime(Instant.now())
                                .build());

                Map<String, String> properties =
                    entity.properties() == null
                        ? Maps.newHashMap()
                        : Maps.newHashMap(entity.properties());

                for (MetricChange change : changes) {
                  if (change instanceof MetricChange.RenameMetric) {
                    builder.withName(((MetricChange.RenameMetric) change).newName());
                  } else if (change instanceof MetricChange.UpdateComment) {
                    builder.withComment(((MetricChange.UpdateComment) change).newComment());
                  } else if (change instanceof MetricChange.SetProperty) {
                    MetricChange.SetProperty setProperty = (MetricChange.SetProperty) change;
                    properties.put(setProperty.property(), setProperty.value());
                  } else if (change instanceof MetricChange.RemoveProperty) {
                    MetricChange.RemoveProperty removeProperty =
                        (MetricChange.RemoveProperty) change;
                    properties.remove(removeProperty.property());
                  } else {
                    throw new IllegalArgumentException(
                        "Unsupported metric change type: " + change.getClass().getSimpleName());
                  }
                }

                builder.withProperties(properties);
                return builder.build();
              });

      return toMetricImpl(updatedEntity);

    } catch (NoSuchEntityException e) {
      throw new NoSuchMetricException(e, "Metric %s does not exist", ident);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to alter metric " + ident, ioe);
    } catch (EntityAlreadyExistsException e) {
      throw new RuntimeException("Metric " + ident + " already exists", e);
    }
  }

  @Override
  public int[] listMetricVersions(NameIdentifier ident) throws NoSuchMetricException {
    NameIdentifierUtil.checkMetric(ident);
    Namespace metricVersionNs = NamespaceUtil.toMetricVersionNs(ident);

    try {
      List<MetricVersionEntity> versions =
          store.list(metricVersionNs, MetricVersionEntity.class, Entity.EntityType.METRIC_VERSION);
      return versions.stream().mapToInt(MetricVersionEntity::version).toArray();

    } catch (NoSuchEntityException e) {
      throw new NoSuchMetricException(e, "Metric %s does not exist", ident);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to list metric versions for metric " + ident, ioe);
    }
  }

  @Override
  public MetricVersion[] listMetricVersionInfos(NameIdentifier ident) throws NoSuchMetricException {
    NameIdentifierUtil.checkMetric(ident);
    Namespace metricVersionNs = NamespaceUtil.toMetricVersionNs(ident);

    try {
      List<MetricVersionEntity> versions =
          store.list(metricVersionNs, MetricVersionEntity.class, Entity.EntityType.METRIC_VERSION);
      return versions.stream().map(this::toMetricVersionImpl).toArray(MetricVersion[]::new);

    } catch (NoSuchEntityException e) {
      throw new NoSuchMetricException(e, "Metric %s does not exist", ident);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to list metric version infos for metric " + ident, ioe);
    }
  }

  @Override
  public MetricVersion getMetricVersion(NameIdentifier ident, int version)
      throws NoSuchMetricVersionException {
    NameIdentifierUtil.checkMetric(ident);
    NameIdentifier versionIdent =
        NameIdentifier.of(NamespaceUtil.toMetricVersionNs(ident), String.valueOf(version));

    try {
      MetricVersionEntity versionEntity =
          store.get(versionIdent, Entity.EntityType.METRIC_VERSION, MetricVersionEntity.class);
      return toMetricVersionImpl(versionEntity);

    } catch (NoSuchEntityException e) {
      throw new NoSuchMetricVersionException(
          e, "Metric version %s (version %d) does not exist", ident, version);
    } catch (IOException ioe) {
      throw new RuntimeException(
          "Failed to get metric version " + ident + " version " + version, ioe);
    }
  }

  @Override
  public boolean deleteMetricVersion(NameIdentifier ident, int version) {
    NameIdentifierUtil.checkMetric(ident);
    NameIdentifier versionIdent =
        NameIdentifier.of(NamespaceUtil.toMetricVersionNs(ident), String.valueOf(version));

    try {
      return store.delete(versionIdent, Entity.EntityType.METRIC_VERSION);
    } catch (IOException ioe) {
      throw new RuntimeException(
          "Failed to delete metric version " + ident + " version " + version, ioe);
    }
  }

  @Override
  public Metric switchMetricVersion(NameIdentifier ident, int targetVersion)
      throws NoSuchMetricException, NoSuchMetricVersionException, IllegalArgumentException {
    NameIdentifierUtil.checkMetric(ident);

    try {
      // 1. 获取当前指标
      MetricEntity metricEntity = store.get(ident, Entity.EntityType.METRIC, MetricEntity.class);

      // 2. 验证目标版本号在有效范围内
      if (targetVersion < 1 || targetVersion > metricEntity.lastVersion()) {
        throw new IllegalArgumentException(
            String.format(
                "Invalid target version %d. Version must be between 1 and %d",
                targetVersion, metricEntity.lastVersion()));
      }

      // 3. 验证目标版本不等于当前版本
      if (metricEntity.currentVersion() == targetVersion) {
        throw new IllegalArgumentException(
            String.format("Metric %s is already on version %d", ident, targetVersion));
      }

      // 4. 验证目标版本存在
      NameIdentifier versionIdent =
          NameIdentifier.of(NamespaceUtil.toMetricVersionNs(ident), String.valueOf(targetVersion));
      store.get(versionIdent, Entity.EntityType.METRIC_VERSION, MetricVersionEntity.class);

      // 5. 使用专用方法切换版本，直接更新数据库，不触发自动版本管理
      MetricEntity updatedEntity =
          MetricMetaService.getInstance().switchMetricCurrentVersion(ident, targetVersion);

      return toMetricImpl(updatedEntity);

    } catch (NoSuchEntityException e) {
      // 判断是指标不存在还是版本不存在
      if (e.getMessage().contains("METRIC_VERSION")) {
        throw new NoSuchMetricVersionException(
            e, "Metric version %s (version %d) does not exist", ident, targetVersion);
      } else {
        throw new NoSuchMetricException(e, "Metric %s does not exist", ident);
      }
    } catch (IOException ioe) {
      throw new RuntimeException(
          "Failed to switch metric " + ident + " to version " + targetVersion, ioe);
    }
  }

  @Override
  public MetricVersion linkMetricVersion(
      NameIdentifier ident,
      String comment,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula)
      throws NoSuchMetricException {
    NameIdentifierUtil.checkMetric(ident);

    try {
      MetricVersionEntity newVersion =
          MetricMetaService.getInstance()
              .createNewMetricVersion(
                  ident, comment, unit, aggregationLogic, parentMetricIds, calculationFormula);
      return toMetricVersionImpl(newVersion);

    } catch (NoSuchEntityException e) {
      throw new NoSuchMetricException(e, "Metric %s does not exist", ident);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to create new version for metric " + ident, ioe);
    }
  }

  @Override
  public MetricVersion alterMetricVersion(
      NameIdentifier ident,
      int version,
      String comment,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula)
      throws NoSuchMetricVersionException {
    NameIdentifierUtil.checkMetric(ident);

    try {
      MetricVersionEntity updatedVersion =
          MetricMetaService.getInstance()
              .updateMetricVersion(
                  ident,
                  version,
                  comment,
                  unit,
                  aggregationLogic,
                  parentMetricIds,
                  calculationFormula);
      return toMetricVersionImpl(updatedVersion);

    } catch (NoSuchEntityException e) {
      throw new NoSuchMetricVersionException(
          e, "Metric version %s (version %d) does not exist", ident, version);
    } catch (IOException ioe) {
      throw new RuntimeException(
          "Failed to alter metric version " + ident + " version " + version, ioe);
    }
  }

  /** 将 MetricEntity 转换为 MetricImpl */
  private Metric toMetricImpl(MetricEntity entity) {
    return MetricImpl.builder()
        .withName(entity.name())
        .withCode(entity.code())
        .withType(entity.metricType())
        .withDataType(entity.dataType())
        .withComment(entity.comment())
        .withProperties(entity.properties())
        .withCurrentVersion(entity.currentVersion())
        .withLastVersion(entity.lastVersion())
        .withAuditInfo(entity.auditInfo())
        .build();
  }

  /** 将 MetricVersionEntity 转换为 MetricVersionImpl */
  private MetricVersion toMetricVersionImpl(MetricVersionEntity entity) {
    return MetricVersionImpl.builder()
        .withVersion(entity.version())
        .withName(entity.metricName())
        .withCode(entity.metricCode())
        .withType(entity.metricType())
        .withDataType(entity.dataType())
        .withComment(entity.comment())
        .withUnit(entity.unit())
        .withAggregationLogic(entity.aggregationLogic())
        .withParentMetricIds(entity.parentMetricIds())
        .withCalculationFormula(entity.calculationFormula())
        .withRefCatalogName(entity.refCatalogName())
        .withRefSchemaName(entity.refSchemaName())
        .withRefTableName(entity.refTableName())
        .withMeasureColumns(entity.measureColumns())
        .withFilterColumns(entity.filterColumns())
        .withProperties(entity.properties())
        .withAuditInfo(entity.auditInfo())
        .build();
  }

  @Override
  public PagedResult<NameIdentifier> listMetricModifiers(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    NamespaceUtil.checkModifier(namespace);

    try {
      List<MetricModifierEntity> modifiers =
          MetricModifierMetaService.getInstance()
              .listModifiersByNamespaceWithPagination(namespace, offset, limit);
      long total = MetricModifierMetaService.getInstance().countModifiersByNamespace(namespace);

      List<NameIdentifier> idents =
          modifiers.stream()
              .map(m -> NameIdentifier.of(namespace, m.code()))
              .collect(Collectors.toList());

      return new PagedResult<>(idents, total, offset, limit);

    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", namespace);
    }
  }

  @Override
  public org.apache.gravitino.dataset.MetricModifier getMetricModifier(NameIdentifier ident) {
    NameIdentifierUtil.checkModifier(ident);

    try {
      org.apache.gravitino.meta.MetricModifierEntity entity =
          store.get(
              ident,
              Entity.EntityType.METRIC_MODIFIER,
              org.apache.gravitino.meta.MetricModifierEntity.class);
      return toModifierImpl(entity);

    } catch (NoSuchEntityException e) {
      throw new RuntimeException("Metric modifier " + ident + " does not exist", e);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to get metric modifier " + ident, ioe);
    }
  }

  @Override
  public org.apache.gravitino.dataset.MetricModifier createMetricModifier(
      NameIdentifier ident,
      String code,
      org.apache.gravitino.dataset.MetricModifier.Type type,
      String comment)
      throws NoSuchSchemaException {
    NameIdentifierUtil.checkModifier(ident);

    long uid = GravitinoEnv.getInstance().idGenerator().nextId();
    org.apache.gravitino.meta.MetricModifierEntity entity =
        org.apache.gravitino.meta.MetricModifierEntity.builder()
            .withId(uid)
            .withName(ident.name())
            .withNamespace(ident.namespace())
            .withCode(code)
            .withType(type)
            .withComment(comment)
            .withAuditInfo(
                AuditInfo.builder()
                    .withCreator(PrincipalUtils.getCurrentPrincipal().getName())
                    .withCreateTime(Instant.now())
                    .build())
            .build();

    try {
      store.put(entity, false /* overwrite */);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create metric modifier " + ident, e);
    } catch (EntityAlreadyExistsException e) {
      throw new RuntimeException("Metric modifier " + ident + " already exists", e);
    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", ident.namespace());
    }

    return toModifierImpl(entity);
  }

  @Override
  public boolean deleteMetricModifier(NameIdentifier ident) {
    NameIdentifierUtil.checkModifier(ident);

    try {
      return store.delete(ident, Entity.EntityType.METRIC_MODIFIER);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to delete metric modifier " + ident, ioe);
    }
  }

  @Override
  public org.apache.gravitino.dataset.MetricModifier alterMetricModifier(
      NameIdentifier ident, org.apache.gravitino.dataset.MetricModifier.Type type, String comment) {
    NameIdentifierUtil.checkModifier(ident);

    try {
      MetricModifierEntity updatedEntity =
          store.update(
              ident,
              MetricModifierEntity.class,
              Entity.EntityType.METRIC_MODIFIER,
              entity ->
                  MetricModifierEntity.builder()
                      .withId(entity.id())
                      .withName(entity.name())
                      .withNamespace(entity.namespace())
                      .withCode(entity.code())
                      .withType(type)
                      .withComment(comment)
                      .withAuditInfo(
                          AuditInfo.builder()
                              .withCreator(entity.auditInfo().creator())
                              .withCreateTime(entity.auditInfo().createTime())
                              .withLastModifier(PrincipalUtils.getCurrentPrincipal().getName())
                              .withLastModifiedTime(Instant.now())
                              .build())
                      .build());

      return toModifierImpl(updatedEntity);

    } catch (NoSuchEntityException e) {
      throw new RuntimeException("Metric modifier " + ident + " does not exist", e);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to alter metric modifier " + ident, ioe);
    } catch (EntityAlreadyExistsException e) {
      throw new RuntimeException("Metric modifier " + ident + " already exists", e);
    }
  }

  @Override
  public PagedResult<NameIdentifier> listWordRoots(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    NamespaceUtil.checkRoot(namespace);

    try {
      List<WordRootEntity> roots =
          WordRootMetaService.getInstance()
              .listWordRootsByNamespaceWithPagination(namespace, offset, limit);
      long total = WordRootMetaService.getInstance().countWordRootsByNamespace(namespace);

      List<NameIdentifier> idents =
          roots.stream()
              .map(r -> NameIdentifier.of(namespace, r.code()))
              .collect(Collectors.toList());

      return new PagedResult<>(idents, total, offset, limit);

    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", namespace);
    }
  }

  @Override
  public org.apache.gravitino.dataset.WordRoot getWordRoot(NameIdentifier ident) {
    NameIdentifierUtil.checkRoot(ident);

    try {
      org.apache.gravitino.meta.WordRootEntity entity =
          store.get(
              ident, Entity.EntityType.WORDROOT, org.apache.gravitino.meta.WordRootEntity.class);
      return toRootImpl(entity);

    } catch (NoSuchEntityException e) {
      throw new RuntimeException("Metric root " + ident + " does not exist", e);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to get metric root " + ident, ioe);
    }
  }

  @Override
  public org.apache.gravitino.dataset.WordRoot createWordRoot(
      NameIdentifier ident, String code, String name, String dataType, String comment)
      throws NoSuchSchemaException {
    NameIdentifierUtil.checkRoot(ident);

    long uid = GravitinoEnv.getInstance().idGenerator().nextId();
    org.apache.gravitino.meta.WordRootEntity entity =
        org.apache.gravitino.meta.WordRootEntity.builder()
            .withId(uid)
            .withCode(code)
            .withRootName(name)
            .withDataType(dataType)
            .withComment(comment)
            .withNamespace(ident.namespace())
            .withAuditInfo(
                AuditInfo.builder()
                    .withCreator(PrincipalUtils.getCurrentPrincipal().getName())
                    .withCreateTime(Instant.now())
                    .build())
            .build();

    try {
      store.put(entity, false /* overwrite */);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create metric root " + ident, e);
    } catch (EntityAlreadyExistsException e) {
      throw new RuntimeException("Metric root " + ident + " already exists", e);
    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", ident.namespace());
    }

    return toRootImpl(entity);
  }

  @Override
  public boolean deleteWordRoot(NameIdentifier ident) {
    NameIdentifierUtil.checkRoot(ident);

    try {
      return store.delete(ident, Entity.EntityType.WORDROOT);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to delete metric root " + ident, ioe);
    }
  }

  @Override
  public org.apache.gravitino.dataset.WordRoot alterWordRoot(
      NameIdentifier ident, String name, String dataType, String comment) {
    NameIdentifierUtil.checkRoot(ident);

    try {
      WordRootEntity updatedEntity =
          store.update(
              ident,
              WordRootEntity.class,
              Entity.EntityType.WORDROOT,
              entity ->
                  WordRootEntity.builder()
                      .withId(entity.id())
                      .withCode(entity.code())
                      .withRootName(name)
                      .withDataType(dataType)
                      .withComment(comment)
                      .withNamespace(entity.namespace())
                      .withAuditInfo(
                          AuditInfo.builder()
                              .withCreator(entity.auditInfo().creator())
                              .withCreateTime(entity.auditInfo().createTime())
                              .withLastModifier(PrincipalUtils.getCurrentPrincipal().getName())
                              .withLastModifiedTime(Instant.now())
                              .build())
                      .build());

      return toRootImpl(updatedEntity);

    } catch (NoSuchEntityException e) {
      throw new RuntimeException("Word root " + ident + " does not exist", e);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to alter word root " + ident, ioe);
    } catch (EntityAlreadyExistsException e) {
      throw new RuntimeException("Word root " + ident + " already exists", e);
    }
  }

  /** 将 MetricModifierEntity 转换为 ModifierImpl */
  private org.apache.gravitino.dataset.MetricModifier toModifierImpl(
      org.apache.gravitino.meta.MetricModifierEntity entity) {
    return ModifierImpl.builder()
        .withName(entity.name())
        .withCode(entity.code())
        .withType(entity.modifierType())
        .withComment(entity.comment())
        .withAuditInfo(entity.auditInfo())
        .build();
  }

  /** 将 WordRootEntity 转换为 WordRootImpl */
  private org.apache.gravitino.dataset.WordRoot toRootImpl(
      org.apache.gravitino.meta.WordRootEntity entity) {
    return WordRootImpl.builder()
        .withCode(entity.code())
        .withName(entity.rootName())
        .withDataType(entity.dataType())
        .withComment(entity.comment())
        .withAuditInfo(entity.auditInfo())
        .build();
  }

  // ============================= Unit 管理 =============================

  @Override
  public PagedResult<NameIdentifier> listUnits(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    NamespaceUtil.checkUnit(namespace);

    try {
      List<UnitEntity> entities =
          UnitMetaService.getInstance()
              .listUnitsByNamespaceWithPagination(namespace, offset, limit);
      long total = UnitMetaService.getInstance().countUnitsByNamespace(namespace);

      List<NameIdentifier> identifiers =
          entities.stream()
              .map(entity -> NameIdentifier.of(namespace, entity.code()))
              .collect(Collectors.toList());

      return new PagedResult<>(identifiers, total, offset, limit);

    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", namespace);
    }
  }

  @Override
  public org.apache.gravitino.dataset.Unit getUnit(NameIdentifier ident) {
    NameIdentifierUtil.checkUnit(ident);

    try {
      UnitEntity entity = store.get(ident, Entity.EntityType.UNIT, UnitEntity.class);
      return toUnitImpl(entity);

    } catch (NoSuchEntityException e) {
      throw new RuntimeException("Unit " + ident + " does not exist", e);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to get unit " + ident, ioe);
    }
  }

  @Override
  public org.apache.gravitino.dataset.Unit createUnit(
      NameIdentifier ident, String code, String name, String symbol, String comment)
      throws NoSuchSchemaException {
    NameIdentifierUtil.checkUnit(ident);

    long uid = GravitinoEnv.getInstance().idGenerator().nextId();
    UnitEntity entity =
        UnitEntity.builder()
            .withId(uid)
            .withCode(code)
            .withUnitName(name)
            .withSymbol(symbol)
            .withComment(comment)
            .withNamespace(ident.namespace())
            .withAuditInfo(
                AuditInfo.builder()
                    .withCreator(PrincipalUtils.getCurrentPrincipal().getName())
                    .withCreateTime(Instant.now())
                    .build())
            .build();

    try {
      store.put(entity, false);
    } catch (EntityAlreadyExistsException e) {
      throw new RuntimeException("Unit " + ident + " already exists", e);
    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", ident.namespace());
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to create unit " + ident, ioe);
    }

    return toUnitImpl(entity);
  }

  @Override
  public boolean deleteUnit(NameIdentifier ident) {
    NameIdentifierUtil.checkUnit(ident);

    try {
      return store.delete(ident, Entity.EntityType.UNIT);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to delete unit " + ident, ioe);
    }
  }

  @Override
  public org.apache.gravitino.dataset.Unit alterUnit(
      NameIdentifier ident, String name, String symbol, String comment) {
    NameIdentifierUtil.checkUnit(ident);

    try {
      UnitEntity updatedEntity =
          store.update(
              ident,
              UnitEntity.class,
              Entity.EntityType.UNIT,
              entity ->
                  UnitEntity.builder()
                      .withId(entity.id())
                      .withCode(entity.code())
                      .withUnitName(name)
                      .withSymbol(symbol)
                      .withComment(comment)
                      .withNamespace(entity.namespace())
                      .withAuditInfo(
                          AuditInfo.builder()
                              .withCreator(entity.auditInfo().creator())
                              .withCreateTime(entity.auditInfo().createTime())
                              .withLastModifier(PrincipalUtils.getCurrentPrincipal().getName())
                              .withLastModifiedTime(Instant.now())
                              .build())
                      .build());

      return toUnitImpl(updatedEntity);

    } catch (NoSuchEntityException e) {
      throw new RuntimeException("Unit " + ident + " does not exist", e);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to alter unit " + ident, ioe);
    } catch (EntityAlreadyExistsException e) {
      throw new RuntimeException("Unit " + ident + " already exists", e);
    }
  }

  /** 将 UnitEntity 转换为 UnitImpl */
  private org.apache.gravitino.dataset.Unit toUnitImpl(UnitEntity entity) {
    return UnitImpl.builder()
        .withCode(entity.code())
        .withName(entity.unitName())
        .withSymbol(entity.symbol())
        .withComment(entity.comment())
        .withAuditInfo(entity.auditInfo())
        .build();
  }

  // ==================== ValueDomain 值域相关方法 ====================

  @Override
  public PagedResult<NameIdentifier> listValueDomains(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    NamespaceUtil.checkValueDomain(namespace);

    try {
      List<ValueDomainEntity> entities =
          ValueDomainMetaService.getInstance()
              .listValueDomainsByNamespaceWithPagination(namespace, offset, limit);
      long total = ValueDomainMetaService.getInstance().countValueDomainsByNamespace(namespace);

      List<NameIdentifier> identifiers =
          entities.stream()
              .map(
                  entity ->
                      NameIdentifier.of(namespace, entity.domainCode() + ":" + entity.itemValue()))
              .collect(Collectors.toList());

      return new PagedResult<>(identifiers, total, offset, limit);

    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", namespace);
    }
  }

  @Override
  public org.apache.gravitino.dataset.ValueDomain getValueDomain(NameIdentifier ident) {
    NameIdentifierUtil.checkValueDomain(ident);

    try {
      ValueDomainEntity entity =
          store.get(ident, Entity.EntityType.VALUE_DOMAIN, ValueDomainEntity.class);
      return toValueDomainImpl(entity);

    } catch (NoSuchEntityException e) {
      throw new RuntimeException("ValueDomain " + ident + " does not exist", e);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to get value domain " + ident, ioe);
    }
  }

  @Override
  public org.apache.gravitino.dataset.ValueDomain createValueDomain(
      NameIdentifier ident,
      String domainCode,
      String domainName,
      org.apache.gravitino.dataset.ValueDomain.Type domainType,
      String itemValue,
      String itemLabel,
      String comment)
      throws NoSuchSchemaException {
    NameIdentifierUtil.checkValueDomain(ident);

    long uid = GravitinoEnv.getInstance().idGenerator().nextId();
    ValueDomainEntity entity =
        ValueDomainEntity.builder()
            .withId(uid)
            .withDomainCode(domainCode)
            .withDomainName(domainName)
            .withDomainType(domainType)
            .withItemValue(itemValue)
            .withItemLabel(itemLabel)
            .withComment(comment)
            .withNamespace(ident.namespace())
            .withAuditInfo(
                AuditInfo.builder()
                    .withCreator(PrincipalUtils.getCurrentPrincipal().getName())
                    .withCreateTime(Instant.now())
                    .build())
            .build();

    try {
      store.put(entity, false);
    } catch (EntityAlreadyExistsException e) {
      throw new RuntimeException("ValueDomain " + ident + " already exists", e);
    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", ident.namespace());
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to create value domain " + ident, ioe);
    }

    // 自动创建关联 Tag（用于 Column 引用 ValueDomain）
    createValueDomainTag(ident, domainCode, domainName, domainType, itemValue, itemLabel, comment);

    return toValueDomainImpl(entity);
  }

  @Override
  public boolean deleteValueDomain(NameIdentifier ident) {
    NameIdentifierUtil.checkValueDomain(ident);

    // 先获取 domainCode 用于删除关联 Tag
    String domainCode = null;
    try {
      ValueDomainEntity entity =
          store.get(ident, Entity.EntityType.VALUE_DOMAIN, ValueDomainEntity.class);
      domainCode = entity.domainCode();
    } catch (NoSuchEntityException | IOException e) {
      // 忽略，继续尝试删除
    }

    try {
      boolean deleted = store.delete(ident, Entity.EntityType.VALUE_DOMAIN);
      if (deleted && domainCode != null) {
        // 删除关联 Tag
        deleteValueDomainTag(ident, domainCode);
      }
      return deleted;
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to delete value domain " + ident, ioe);
    }
  }

  @Override
  public org.apache.gravitino.dataset.ValueDomain alterValueDomain(
      NameIdentifier ident, String domainName, String itemLabel, String comment) {
    NameIdentifierUtil.checkValueDomain(ident);

    try {
      ValueDomainEntity updatedEntity =
          store.update(
              ident,
              ValueDomainEntity.class,
              Entity.EntityType.VALUE_DOMAIN,
              entity ->
                  ValueDomainEntity.builder()
                      .withId(entity.id())
                      .withDomainCode(entity.domainCode())
                      .withDomainName(domainName)
                      .withDomainType(entity.domainType())
                      .withItemValue(entity.itemValue())
                      .withItemLabel(itemLabel)
                      .withComment(comment)
                      .withNamespace(entity.namespace())
                      .withAuditInfo(
                          AuditInfo.builder()
                              .withCreator(entity.auditInfo().creator())
                              .withCreateTime(entity.auditInfo().createTime())
                              .withLastModifier(PrincipalUtils.getCurrentPrincipal().getName())
                              .withLastModifiedTime(Instant.now())
                              .build())
                      .build());

      return toValueDomainImpl(updatedEntity);

    } catch (NoSuchEntityException e) {
      throw new RuntimeException("ValueDomain " + ident + " does not exist", e);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to alter value domain " + ident, ioe);
    } catch (EntityAlreadyExistsException e) {
      throw new RuntimeException("ValueDomain " + ident + " already exists", e);
    }
  }

  /** ValueDomain Tag 前缀 */
  private static final String VALUE_DOMAIN_TAG_PREFIX = "vd:";

  /**
   * 创建 ValueDomain 关联的 Tag
   *
   * <p>Tag 命名规则：vd:{domainCode}，用于 Column 引用 ValueDomain
   */
  private void createValueDomainTag(
      NameIdentifier ident,
      String domainCode,
      String domainName,
      org.apache.gravitino.dataset.ValueDomain.Type domainType,
      String itemValue,
      String itemLabel,
      String comment) {
    try {
      String metalake = ident.namespace().level(0);
      String tagName = VALUE_DOMAIN_TAG_PREFIX + domainCode;
      String tagComment =
          (domainName != null ? domainName : domainCode) + (comment != null ? ": " + comment : "");

      // Tag properties 存储 ValueDomain 详细信息
      Map<String, String> properties = Maps.newHashMap();
      properties.put("domainCode", domainCode);
      properties.put("domainType", domainType.name());
      if (domainName != null) {
        properties.put("domainName", domainName);
      }
      if (itemValue != null) {
        properties.put("itemValue", itemValue);
      }
      if (itemLabel != null) {
        properties.put("itemLabel", itemLabel);
      }

      TagDispatcher tagDispatcher = GravitinoEnv.getInstance().tagDispatcher();
      tagDispatcher.createTag(metalake, tagName, tagComment, properties);
    } catch (Exception e) {
      // Tag 创建失败不影响 ValueDomain 创建，仅记录警告
      // 可能是 Tag 已存在或其他原因
    }
  }

  /** 删除 ValueDomain 关联的 Tag */
  private void deleteValueDomainTag(NameIdentifier ident, String domainCode) {
    try {
      String metalake = ident.namespace().level(0);
      String tagName = VALUE_DOMAIN_TAG_PREFIX + domainCode;

      TagDispatcher tagDispatcher = GravitinoEnv.getInstance().tagDispatcher();
      tagDispatcher.deleteTag(metalake, tagName);
    } catch (Exception e) {
      // Tag 删除失败不影响 ValueDomain 删除
    }
  }

  /** 将 ValueDomainEntity 转换为 ValueDomainImpl */
  private org.apache.gravitino.dataset.ValueDomain toValueDomainImpl(ValueDomainEntity entity) {
    return ValueDomainImpl.builder()
        .withDomainCode(entity.domainCode())
        .withDomainName(entity.domainName())
        .withDomainType(entity.domainType())
        .withItemValue(entity.itemValue())
        .withItemLabel(entity.itemLabel())
        .withComment(entity.comment())
        .withAuditInfo(entity.auditInfo())
        .build();
  }
}
