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
package org.apache.gravitino.catalog.metric;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.apache.gravitino.exceptions.MetricAlreadyExistsException;
import org.apache.gravitino.exceptions.NoSuchEntityException;
import org.apache.gravitino.exceptions.NoSuchMetricException;
import org.apache.gravitino.exceptions.NoSuchMetricVersionException;
import org.apache.gravitino.exceptions.NoSuchSchemaException;
import org.apache.gravitino.meta.AuditInfo;
import org.apache.gravitino.meta.MetricEntity;
import org.apache.gravitino.meta.MetricVersionEntity;
import org.apache.gravitino.metric.Metric;
import org.apache.gravitino.metric.MetricCatalog;
import org.apache.gravitino.metric.MetricChange;
import org.apache.gravitino.metric.MetricVersion;
import org.apache.gravitino.storage.relational.service.MetricMetaService;
import org.apache.gravitino.utils.NameIdentifierUtil;
import org.apache.gravitino.utils.NamespaceUtil;
import org.apache.gravitino.utils.PrincipalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Metric Catalog 操作类，实现所有 Metric 相关的 CRUD 操作 */
public class MetricCatalogOperations extends ManagedSchemaOperations
    implements CatalogOperations, MetricCatalog {

  private static final Logger LOG = LoggerFactory.getLogger(MetricCatalogOperations.class);
  private static final int INIT_VERSION = 1;

  private final EntityStore store;

  public MetricCatalogOperations(EntityStore store) {
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
    // Metric catalog 不需要连接测试
  }

  @Override
  protected EntityStore store() {
    return store;
  }

  @Override
  public NameIdentifier[] listMetrics(Namespace namespace) throws NoSuchSchemaException {
    NamespaceUtil.checkMetric(namespace);

    try {
      List<MetricEntity> metrics =
          store.list(namespace, MetricEntity.class, Entity.EntityType.METRIC);
      return metrics.stream()
          .map(m -> NameIdentifier.of(namespace, m.code()))
          .toArray(NameIdentifier[]::new);

    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", namespace);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to list metrics under namespace " + namespace, ioe);
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
      String comment,
      Map<String, String> properties,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula)
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
              metric, unit, aggregationLogic, parentMetricIds, calculationFormula);
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

  /** 将 MetricEntity 转换为 MetricImpl */
  private Metric toMetricImpl(MetricEntity entity) {
    return MetricImpl.builder()
        .withName(entity.name())
        .withCode(entity.code())
        .withType(entity.metricType())
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
        .withComment(entity.comment())
        .withUnit(entity.unit())
        .withAggregationLogic(entity.aggregationLogic())
        .withParentMetricIds(entity.parentMetricIds())
        .withCalculationFormula(entity.calculationFormula())
        .withProperties(entity.properties())
        .withAuditInfo(entity.auditInfo())
        .build();
  }

  @Override
  public NameIdentifier[] listMetricModifiers(Namespace namespace) throws NoSuchSchemaException {
    NamespaceUtil.checkModifier(namespace);

    try {
      List<org.apache.gravitino.meta.MetricModifierEntity> modifiers =
          store.list(
              namespace,
              org.apache.gravitino.meta.MetricModifierEntity.class,
              Entity.EntityType.METRIC_MODIFIER);
      return modifiers.stream()
          .map(m -> NameIdentifier.of(namespace, m.code()))
          .toArray(NameIdentifier[]::new);

    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", namespace);
    } catch (IOException ioe) {
      throw new RuntimeException(
          "Failed to list metric modifiers under namespace " + namespace, ioe);
    }
  }

  @Override
  public org.apache.gravitino.metric.MetricModifier getMetricModifier(NameIdentifier ident) {
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
  public org.apache.gravitino.metric.MetricModifier createMetricModifier(
      NameIdentifier ident,
      String code,
      org.apache.gravitino.metric.MetricModifier.Type type,
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
  public NameIdentifier[] listMetricRoots(Namespace namespace) throws NoSuchSchemaException {
    NamespaceUtil.checkRoot(namespace);

    try {
      List<org.apache.gravitino.meta.MetricRootEntity> roots =
          store.list(
              namespace,
              org.apache.gravitino.meta.MetricRootEntity.class,
              Entity.EntityType.METRIC_ROOT);
      LOG.info("DEBUG: listMetricRoots - 从 store.list 获取到 {} 个 Entity", roots.size());
      NameIdentifier[] result =
          roots.stream()
              .map(r -> NameIdentifier.of(namespace, r.code()))
              .toArray(NameIdentifier[]::new);
      LOG.info("DEBUG: listMetricRoots - 转换后返回 {} 个 NameIdentifier", result.length);
      for (int i = 0; i < result.length; i++) {
        LOG.info("DEBUG: listMetricRoots - NameIdentifier[{}]: {}", i, result[i]);
      }
      return result;

    } catch (NoSuchEntityException e) {
      throw new NoSuchSchemaException(e, "Schema %s does not exist", namespace);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to list metric roots under namespace " + namespace, ioe);
    }
  }

  @Override
  public org.apache.gravitino.metric.MetricRoot getMetricRoot(NameIdentifier ident) {
    NameIdentifierUtil.checkRoot(ident);

    try {
      org.apache.gravitino.meta.MetricRootEntity entity =
          store.get(
              ident,
              Entity.EntityType.METRIC_ROOT,
              org.apache.gravitino.meta.MetricRootEntity.class);
      return toRootImpl(entity);

    } catch (NoSuchEntityException e) {
      throw new RuntimeException("Metric root " + ident + " does not exist", e);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to get metric root " + ident, ioe);
    }
  }

  @Override
  public org.apache.gravitino.metric.MetricRoot createMetricRoot(
      NameIdentifier ident, String code, String nameCn, String nameEn, String comment)
      throws NoSuchSchemaException {
    NameIdentifierUtil.checkRoot(ident);

    long uid = GravitinoEnv.getInstance().idGenerator().nextId();
    org.apache.gravitino.meta.MetricRootEntity entity =
        org.apache.gravitino.meta.MetricRootEntity.builder()
            .withId(uid)
            .withCode(code)
            .withNameCn(nameCn)
            .withNameEn(nameEn)
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
  public boolean deleteMetricRoot(NameIdentifier ident) {
    NameIdentifierUtil.checkRoot(ident);

    try {
      return store.delete(ident, Entity.EntityType.METRIC_ROOT);
    } catch (IOException ioe) {
      throw new RuntimeException("Failed to delete metric root " + ident, ioe);
    }
  }

  /** 将 MetricModifierEntity 转换为 ModifierImpl */
  private org.apache.gravitino.metric.MetricModifier toModifierImpl(
      org.apache.gravitino.meta.MetricModifierEntity entity) {
    return ModifierImpl.builder()
        .withName(entity.name())
        .withCode(entity.code())
        .withType(entity.modifierType())
        .withComment(entity.comment())
        .withAuditInfo(entity.auditInfo())
        .build();
  }

  /** 将 MetricRootEntity 转换为 RootImpl */
  private org.apache.gravitino.metric.MetricRoot toRootImpl(
      org.apache.gravitino.meta.MetricRootEntity entity) {
    return RootImpl.builder()
        .withCode(entity.code())
        .withNameCn(entity.nameCn())
        .withNameEn(entity.nameEn())
        .withComment(entity.comment())
        .withAuditInfo(entity.auditInfo())
        .build();
  }
}
