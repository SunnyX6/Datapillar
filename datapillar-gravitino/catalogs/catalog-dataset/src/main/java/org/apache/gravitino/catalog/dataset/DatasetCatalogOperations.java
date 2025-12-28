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
import org.apache.gravitino.dataset.MetricModifier;
import org.apache.gravitino.dataset.MetricVersion;
import org.apache.gravitino.dataset.Unit;
import org.apache.gravitino.dataset.ValueDomain;
import org.apache.gravitino.dataset.WordRoot;
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
  public PagedResult<Metric> listMetrics(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    NamespaceUtil.checkMetric(namespace);

    try {
      List<MetricEntity> metrics =
          MetricMetaService.getInstance()
              .listMetricsByNamespaceWithPagination(namespace, offset, limit);
      long total = MetricMetaService.getInstance().countMetricsByNamespace(namespace);

      List<Metric> metricList =
          metrics.stream().map(this::toMetricImpl).collect(Collectors.toList());

      return new PagedResult<>(metricList, total, offset, limit);

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
      String name,
      String code,
      Metric.Type type,
      String dataType,
      String comment,
      Map<String, String> properties,
      String unit,
      String[] parentMetricCodes,
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

    // 根据 unit 代码查询单位名称
    String unitName = null;
    if (unit != null && !unit.isEmpty()) {
      try {
        NameIdentifier unitIdent = NameIdentifier.of(ident.namespace(), unit);
        UnitEntity unitEntity = UnitMetaService.getInstance().getUnitByIdentifier(unitIdent);
        unitName = unitEntity.name();
      } catch (Exception e) {
        // 单位不存在时忽略
      }
    }

    MetricEntity metric =
        MetricEntity.builder()
            .withId(stringId.id())
            .withName(name)
            .withNamespace(ident.namespace())
            .withCode(code)
            .withType(type)
            .withDataType(dataType)
            .withUnit(unit)
            .withUnitName(unitName)
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
              parentMetricCodes,
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
                  } else if (change instanceof MetricChange.UpdateDataType) {
                    builder.withDataType(((MetricChange.UpdateDataType) change).newDataType());
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
      throw new EntityAlreadyExistsException("Metric %s already exists", ident);
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
  public MetricVersion switchMetricVersion(NameIdentifier ident, int targetVersion)
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

      // 4. 切换版本并返回目标版本详情
      MetricVersionEntity versionEntity =
          MetricMetaService.getInstance().switchMetricCurrentVersion(ident, targetVersion);

      return toMetricVersionImpl(versionEntity);

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
      String refCatalogName,
      String refSchemaName,
      String refTableName,
      String measureColumns,
      String filterColumns)
      throws NoSuchMetricVersionException {
    NameIdentifierUtil.checkMetric(ident);

    try {
      MetricVersionEntity updatedVersion =
          MetricMetaService.getInstance()
              .updateMetricVersion(
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
                  refCatalogName,
                  refSchemaName,
                  refTableName,
                  measureColumns,
                  filterColumns);
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
        .withUnit(entity.unit())
        .withUnitName(entity.unitName())
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
        .withId(entity.id())
        .withVersion(entity.version())
        .withName(entity.metricName())
        .withCode(entity.metricCode())
        .withType(entity.metricType())
        .withDataType(entity.dataType())
        .withComment(entity.comment())
        .withUnit(entity.unit())
        .withUnitName(entity.unitName())
        .withUnitSymbol(entity.unitSymbol())
        .withParentMetricCodes(entity.parentMetricCodes())
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
  public PagedResult<MetricModifier> listMetricModifiers(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    NamespaceUtil.checkModifier(namespace);

    try {
      List<MetricModifierEntity> modifiers =
          MetricModifierMetaService.getInstance()
              .listModifiersByNamespaceWithPagination(namespace, offset, limit);
      long total = MetricModifierMetaService.getInstance().countModifiersByNamespace(namespace);

      List<MetricModifier> modifierList =
          modifiers.stream().map(this::toModifierImpl).collect(Collectors.toList());

      return new PagedResult<>(modifierList, total, offset, limit);

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
      NameIdentifier ident, String code, String comment, String modifierType)
      throws NoSuchSchemaException {
    NameIdentifierUtil.checkModifier(ident);

    long uid = GravitinoEnv.getInstance().idGenerator().nextId();
    org.apache.gravitino.meta.MetricModifierEntity entity =
        org.apache.gravitino.meta.MetricModifierEntity.builder()
            .withId(uid)
            .withName(ident.name())
            .withNamespace(ident.namespace())
            .withCode(code)
            .withComment(comment)
            .withModifierType(modifierType)
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
      throw new EntityAlreadyExistsException("Metric modifier %s already exists", ident);
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
      NameIdentifier ident, String name, String comment) {
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
                      .withName(name != null ? name : entity.name())
                      .withNamespace(entity.namespace())
                      .withCode(entity.code())
                      .withComment(comment != null ? comment : entity.comment())
                      .withModifierType(entity.modifierType())
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
      throw new EntityAlreadyExistsException("Metric modifier %s already exists", ident);
    }
  }

  @Override
  public PagedResult<WordRoot> listWordRoots(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    NamespaceUtil.checkRoot(namespace);

    try {
      List<WordRootEntity> roots =
          WordRootMetaService.getInstance()
              .listWordRootsByNamespaceWithPagination(namespace, offset, limit);
      long total = WordRootMetaService.getInstance().countWordRootsByNamespace(namespace);

      List<WordRoot> wordRoots = roots.stream().map(this::toRootImpl).collect(Collectors.toList());

      return new PagedResult<>(wordRoots, total, offset, limit);

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
      throw new EntityAlreadyExistsException("Word root %s already exists", ident);
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
      throw new EntityAlreadyExistsException("Word root %s already exists", ident);
    }
  }

  /** 将 MetricModifierEntity 转换为 ModifierImpl */
  private org.apache.gravitino.dataset.MetricModifier toModifierImpl(
      org.apache.gravitino.meta.MetricModifierEntity entity) {
    return ModifierImpl.builder()
        .withName(entity.name())
        .withCode(entity.code())
        .withComment(entity.comment())
        .withModifierType(entity.modifierType())
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
  public PagedResult<Unit> listUnits(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    NamespaceUtil.checkUnit(namespace);

    try {
      List<UnitEntity> entities =
          UnitMetaService.getInstance()
              .listUnitsByNamespaceWithPagination(namespace, offset, limit);
      long total = UnitMetaService.getInstance().countUnitsByNamespace(namespace);

      List<Unit> unitList = entities.stream().map(this::toUnitImpl).collect(Collectors.toList());

      return new PagedResult<>(unitList, total, offset, limit);

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
      throw new EntityAlreadyExistsException("Unit %s already exists", ident);
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
      throw new EntityAlreadyExistsException("Unit %s already exists", ident);
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
  public PagedResult<ValueDomain> listValueDomains(Namespace namespace, int offset, int limit)
      throws NoSuchSchemaException {
    NamespaceUtil.checkValueDomain(namespace);

    try {
      List<ValueDomainEntity> entities =
          ValueDomainMetaService.getInstance()
              .listValueDomainsByNamespaceWithPagination(namespace, offset, limit);
      long total = ValueDomainMetaService.getInstance().countValueDomainsByNamespace(namespace);

      List<ValueDomain> valueDomainList =
          entities.stream().map(this::toValueDomainImpl).collect(Collectors.toList());

      return new PagedResult<>(valueDomainList, total, offset, limit);

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
      org.apache.gravitino.dataset.ValueDomain.Level domainLevel,
      java.util.List<org.apache.gravitino.dataset.ValueDomain.Item> items,
      String comment,
      String dataType)
      throws NoSuchSchemaException {
    NameIdentifierUtil.checkValueDomain(ident);

    // 默认级别为 BUSINESS
    org.apache.gravitino.dataset.ValueDomain.Level level =
        domainLevel != null ? domainLevel : org.apache.gravitino.dataset.ValueDomain.Level.BUSINESS;

    long uid = GravitinoEnv.getInstance().idGenerator().nextId();
    ValueDomainEntity entity =
        ValueDomainEntity.builder()
            .withId(uid)
            .withDomainCode(domainCode)
            .withDomainName(domainName)
            .withDomainType(domainType)
            .withDomainLevel(level)
            .withItems(items)
            .withComment(comment)
            .withDataType(dataType)
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
      throw new EntityAlreadyExistsException("ValueDomain %s already exists", ident);
    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", ident.namespace());
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to create value domain " + ident, ioe);
    }

    // 自动创建关联 Tag（用于 Column 引用 ValueDomain）
    createValueDomainTag(ident, domainCode, domainName, comment);

    return toValueDomainImpl(entity);
  }

  @Override
  public boolean deleteValueDomain(NameIdentifier ident) {
    NameIdentifierUtil.checkValueDomain(ident);

    // 先获取实体，检查是否为内置值域
    String domainCode = null;
    try {
      ValueDomainEntity entity =
          store.get(ident, Entity.EntityType.VALUE_DOMAIN, ValueDomainEntity.class);
      domainCode = entity.domainCode();

      // 内置值域禁止删除
      if (entity.domainLevel() == org.apache.gravitino.dataset.ValueDomain.Level.BUILTIN) {
        throw new UnsupportedOperationException(
            "Cannot delete builtin value domain: " + ident.name());
      }
    } catch (NoSuchEntityException e) {
      // 实体不存在，直接返回 false
      return false;
    } catch (IOException e) {
      throw new RuntimeException("Failed to get value domain " + ident, e);
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
      NameIdentifier ident,
      String domainName,
      org.apache.gravitino.dataset.ValueDomain.Level domainLevel,
      java.util.List<org.apache.gravitino.dataset.ValueDomain.Item> items,
      String comment,
      String dataType) {
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
                      .withDomainName(domainName != null ? domainName : entity.domainName())
                      .withDomainType(entity.domainType())
                      .withDomainLevel(domainLevel != null ? domainLevel : entity.domainLevel())
                      .withItems(items != null ? items : entity.items())
                      .withComment(comment != null ? comment : entity.comment())
                      .withDataType(dataType != null ? dataType : entity.dataType())
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
      throw new EntityAlreadyExistsException("ValueDomain %s already exists", ident);
    }
  }

  /** ValueDomain Tag 前缀 */
  private static final String VALUE_DOMAIN_TAG_PREFIX = "vd:";

  /**
   * 创建 ValueDomain 关联的 Tag
   *
   * <p>Tag 命名规则：vd:{domainCode}，用于 Column 引用 ValueDomain
   *
   * <p>Tag 只作为引用指针，不存储值域详情。查询值域详情时通过 domainCode 查询值域表。
   */
  private void createValueDomainTag(
      NameIdentifier ident, String domainCode, String domainName, String comment) {
    try {
      String metalake = ident.namespace().level(0);
      String tagName = VALUE_DOMAIN_TAG_PREFIX + domainCode;
      String tagComment =
          (domainName != null ? domainName : domainCode) + (comment != null ? ": " + comment : "");

      TagDispatcher tagDispatcher = GravitinoEnv.getInstance().tagDispatcher();

      // 检查 Tag 是否已存在，不存在则创建
      try {
        tagDispatcher.getTag(metalake, tagName);
        // Tag 已存在，无需重复创建
      } catch (Exception e) {
        // Tag 不存在，创建新的（properties 为空，值域详情通过查询值域表获取）
        tagDispatcher.createTag(metalake, tagName, tagComment, Maps.newHashMap());
      }
    } catch (Exception e) {
      // Tag 创建失败不影响 ValueDomain 创建
    }
  }

  /** 转义 JSON 字符串中的特殊字符 */
  @SuppressWarnings("unused")
  private String escapeJson(String str) {
    if (str == null) {
      return "";
    }
    return str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
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
        .withDomainLevel(entity.domainLevel())
        .withItems(entity.items())
        .withComment(entity.comment())
        .withDataType(entity.dataType())
        .withAuditInfo(entity.auditInfo())
        .build();
  }
}
