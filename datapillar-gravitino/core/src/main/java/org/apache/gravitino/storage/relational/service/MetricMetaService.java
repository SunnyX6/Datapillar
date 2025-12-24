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
package org.apache.gravitino.storage.relational.service;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.gravitino.Entity;
import org.apache.gravitino.HasIdentifier;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.dataset.Metric;
import org.apache.gravitino.exceptions.NoSuchEntityException;
import org.apache.gravitino.meta.AuditInfo;
import org.apache.gravitino.meta.MetricEntity;
import org.apache.gravitino.meta.MetricVersionEntity;
import org.apache.gravitino.storage.relational.mapper.MetricMetaMapper;
import org.apache.gravitino.storage.relational.mapper.MetricVersionMetaMapper;
import org.apache.gravitino.storage.relational.po.MetricPO;
import org.apache.gravitino.storage.relational.po.MetricVersionPO;
import org.apache.gravitino.storage.relational.utils.ExceptionUtils;
import org.apache.gravitino.storage.relational.utils.POConverters;
import org.apache.gravitino.storage.relational.utils.SessionUtils;
import org.apache.gravitino.utils.NameIdentifierUtil;
import org.apache.gravitino.utils.NamespaceUtil;
import org.apache.gravitino.utils.PrincipalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Service 类处理 Metric 和 MetricVersion 的数据库操作 */
public class MetricMetaService {

  private static final Logger LOG = LoggerFactory.getLogger(MetricMetaService.class);
  private static final MetricMetaService INSTANCE = new MetricMetaService();

  public static MetricMetaService getInstance() {
    return INSTANCE;
  }

  private MetricMetaService() {}

  /**
   * 根据 namespace 列出所有指标
   *
   * @param ns namespace (应该是 schema 级别)
   * @return 指标实体列表
   */
  public List<MetricEntity> listMetricsByNamespace(Namespace ns) {
    NamespaceUtil.checkMetric(ns);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ns);

    List<MetricPO> metricPOs =
        SessionUtils.getWithoutCommit(
            MetricMetaMapper.class, mapper -> mapper.listMetricPOsBySchemaId(schemaId));

    return POConverters.fromMetricPOs(metricPOs, ns);
  }

  /**
   * 分页列出指标
   *
   * @param ns namespace (应该是 schema 级别)
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 指标实体列表
   */
  public List<MetricEntity> listMetricsByNamespaceWithPagination(
      Namespace ns, int offset, int limit) {
    NamespaceUtil.checkMetric(ns);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ns);

    List<MetricPO> metricPOs =
        SessionUtils.getWithoutCommit(
            MetricMetaMapper.class,
            mapper -> mapper.listMetricPOsBySchemaIdWithPagination(schemaId, offset, limit));

    return POConverters.fromMetricPOs(metricPOs, ns);
  }

  /**
   * 统计指标总数
   *
   * @param ns namespace (应该是 schema 级别)
   * @return 指标总数
   */
  public long countMetricsByNamespace(Namespace ns) {
    NamespaceUtil.checkMetric(ns);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ns);

    return SessionUtils.getWithoutCommit(
        MetricMetaMapper.class, mapper -> mapper.countMetricsBySchemaId(schemaId));
  }

  /**
   * 根据标识符获取指标
   *
   * @param ident 指标的 NameIdentifier
   * @return 指标实体
   */
  public MetricEntity getMetricByIdentifier(NameIdentifier ident) {
    MetricPO metricPO = getMetricPOByIdentifier(ident);
    return POConverters.fromMetricPO(metricPO, ident.namespace());
  }

  /**
   * 插入新指标
   *
   * @param metricEntity 指标实体
   * @param overwrite 是否覆盖已存在的指标
   * @throws IOException 如果插入失败
   */
  public void insertMetric(MetricEntity metricEntity, boolean overwrite) throws IOException {
    insertMetricWithVersion(
        metricEntity, overwrite, null, null, null, null, null, null, null, null, null);
  }

  /**
   * 插入新指标并设置初始版本的属性
   *
   * @param metricEntity 指标实体
   * @param unit 单位
   * @param aggregationLogic 聚合逻辑
   * @param parentMetricIds 父指标IDs
   * @param calculationFormula 计算公式
   * @param refCatalogName 引用的数据源名称
   * @param refSchemaName 引用的数据库名称
   * @param refTableName 引用的表名称
   * @param measureColumns 度量列JSON
   * @param filterColumns 过滤列JSON
   * @throws IOException 如果插入失败
   */
  public void insertMetricWithVersion(
      MetricEntity metricEntity,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula,
      String refCatalogName,
      String refSchemaName,
      String refTableName,
      String measureColumns,
      String filterColumns)
      throws IOException {
    insertMetricWithVersion(
        metricEntity,
        false,
        unit,
        aggregationLogic,
        parentMetricIds,
        calculationFormula,
        refCatalogName,
        refSchemaName,
        refTableName,
        measureColumns,
        filterColumns);
  }

  /**
   * 插入新指标并设置初始版本的属性（内部方法）
   *
   * @param metricEntity 指标实体
   * @param overwrite 是否覆盖
   * @param unit 单位
   * @param aggregationLogic 聚合逻辑
   * @param parentMetricIds 父指标IDs
   * @param calculationFormula 计算公式
   * @param refCatalogName 引用的数据源名称
   * @param refSchemaName 引用的数据库名称
   * @param refTableName 引用的表名称
   * @param measureColumns 度量列JSON
   * @param filterColumns 过滤列JSON
   * @throws IOException 如果插入失败
   */
  private void insertMetricWithVersion(
      MetricEntity metricEntity,
      boolean overwrite,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula,
      String refCatalogName,
      String refSchemaName,
      String refTableName,
      String measureColumns,
      String filterColumns)
      throws IOException {
    NameIdentifierUtil.checkMetric(metricEntity.nameIdentifier());

    try {
      MetricPO.Builder builder = MetricPO.builder();
      fillMetricPOBuilderParentEntityId(builder, metricEntity.namespace());

      SessionUtils.doMultipleWithCommit(
          () ->
              SessionUtils.doWithoutCommit(
                  MetricMetaMapper.class,
                  mapper -> {
                    MetricPO po = POConverters.initializeMetricPOWithVersion(metricEntity, builder);
                    if (overwrite) {
                      mapper.insertMetricMetaOnDuplicateKeyUpdate(po);
                    } else {
                      mapper.insertMetricMeta(po);
                    }
                  }),
          () -> {
            // 同时插入第一个版本到 metric_version_info 表，并设置版本相关属性
            MetricVersionEntity initialVersion =
                POConverters.createInitialMetricVersion(
                    metricEntity,
                    unit,
                    aggregationLogic,
                    parentMetricIds,
                    calculationFormula,
                    refCatalogName,
                    refSchemaName,
                    refTableName,
                    measureColumns,
                    filterColumns);
            MetricPO metricPO = builder.build();
            MetricVersionPO versionPO =
                POConverters.initializeMetricVersionPO(
                    initialVersion,
                    metricPO.getMetricId(),
                    metricPO.getMetalakeId(),
                    metricPO.getCatalogId(),
                    metricPO.getSchemaId());
            SessionUtils.doWithoutCommit(
                MetricVersionMetaMapper.class, mapper -> mapper.insertMetricVersionMeta(versionPO));
          });
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.METRIC, metricEntity.nameIdentifier().toString());
      throw re;
    }
  }

  /**
   * 更新指标（只更新描述性字段：name、comment，不创建版本）
   *
   * <p>版本创建应通过 linkMetricVersion 显式调用
   *
   * @param identifier 指标标识符
   * @param updater 更新函数
   * @param <E> 实体类型
   * @return 更新后的指标实体
   * @throws IOException 如果更新失败
   */
  public <E extends Entity & HasIdentifier> MetricEntity updateMetric(
      NameIdentifier identifier, Function<E, E> updater) throws IOException {
    NameIdentifierUtil.checkMetric(identifier);

    MetricPO oldMetricPO = getMetricPOByIdentifier(identifier);
    MetricEntity oldMetricEntity = POConverters.fromMetricPO(oldMetricPO, identifier.namespace());
    MetricEntity newEntity = (MetricEntity) updater.apply((E) oldMetricEntity);

    Preconditions.checkArgument(
        Objects.equals(oldMetricEntity.id(), newEntity.id()),
        "The updated metric entity id: %s should be same with the metric entity id before: %s",
        newEntity.id(),
        oldMetricEntity.id());

    Integer updateResult;
    try {
      // 只更新 metric_meta 表的描述性字段，不创建版本
      updateResult =
          SessionUtils.doWithCommitAndFetchResult(
              MetricMetaMapper.class,
              mapper ->
                  mapper.updateMetricMeta(
                      POConverters.updateMetricPOWithVersion(oldMetricPO, newEntity, false),
                      oldMetricPO));
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.METRIC, newEntity.nameIdentifier().toString());
      throw re;
    }

    if (updateResult > 0) {
      return newEntity;
    } else {
      throw new IOException("Failed to update the entity: " + identifier);
    }
  }

  /**
   * 删除指标
   *
   * @param ident 指标标识符
   * @return 是否删除成功
   */
  public boolean deleteMetric(NameIdentifier ident) {
    NameIdentifierUtil.checkMetric(ident);

    Long schemaId;
    String metricCode = ident.name();

    try {
      schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    } catch (NoSuchEntityException e) {
      LOG.warn("Failed to delete metric: {}", ident, e);
      return false;
    }

    int[] metricDeletedCount = new int[] {0};
    int[] metricVersionDeletedCount = new int[] {0};

    // 同时删除 metric_meta 和 metric_version_info 表的记录
    SessionUtils.doMultipleWithCommit(
        () ->
            metricVersionDeletedCount[0] =
                SessionUtils.getWithoutCommit(
                    MetricVersionMetaMapper.class,
                    mapper ->
                        mapper.softDeleteMetricVersionsBySchemaIdAndMetricCode(
                            schemaId, metricCode)),
        () ->
            metricDeletedCount[0] =
                SessionUtils.getWithoutCommit(
                    MetricMetaMapper.class,
                    mapper ->
                        mapper.softDeleteMetricMetaBySchemaIdAndMetricCode(schemaId, metricCode)));

    return metricDeletedCount[0] + metricVersionDeletedCount[0] > 0;
  }

  /**
   * 切换指标的当前版本（直接更新数据库，不触发版本创建逻辑）
   *
   * @param ident 指标标识符
   * @param targetVersion 目标版本号
   * @return 更新后的指标实体
   * @throws IOException 如果更新失败
   */
  public MetricEntity switchMetricCurrentVersion(NameIdentifier ident, int targetVersion)
      throws IOException {
    NameIdentifierUtil.checkMetric(ident);

    MetricPO oldMetricPO = getMetricPOByIdentifier(ident);

    // 直接更新current_version字段
    MetricPO updatedPO =
        MetricPO.builder()
            .withMetricId(oldMetricPO.getMetricId())
            .withMetricName(oldMetricPO.getMetricName())
            .withMetricCode(oldMetricPO.getMetricCode())
            .withMetricType(oldMetricPO.getMetricType())
            .withMetalakeId(oldMetricPO.getMetalakeId())
            .withCatalogId(oldMetricPO.getCatalogId())
            .withSchemaId(oldMetricPO.getSchemaId())
            .withMetricComment(oldMetricPO.getMetricComment())
            .withCurrentVersion(targetVersion) // 只修改这个字段
            .withLastVersion(oldMetricPO.getLastVersion())
            .withAuditInfo(oldMetricPO.getAuditInfo())
            .withDeletedAt(oldMetricPO.getDeletedAt())
            .build();

    Integer updateResult;
    try {
      updateResult =
          SessionUtils.doWithCommitAndFetchResult(
              MetricMetaMapper.class, mapper -> mapper.updateMetricMeta(updatedPO, oldMetricPO));
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(re, Entity.EntityType.METRIC, ident.toString());
      throw re;
    }

    if (updateResult > 0) {
      return POConverters.fromMetricPO(updatedPO, ident.namespace());
    } else {
      throw new IOException("Failed to switch metric version: " + ident);
    }
  }

  /**
   * 根据遗留时间线删除指标元数据
   *
   * @param legacyTimeline 遗留时间线
   * @param limit 删除数量限制
   * @return 删除的记录数
   */
  public int deleteMetricMetasByLegacyTimeline(Long legacyTimeline, int limit) {
    int metricDeletedCount =
        SessionUtils.doWithCommitAndFetchResult(
            MetricMetaMapper.class,
            mapper -> mapper.deleteMetricMetasByLegacyTimeline(legacyTimeline, limit));

    int metricVersionDeletedCount =
        SessionUtils.doWithCommitAndFetchResult(
            MetricVersionMetaMapper.class,
            mapper -> mapper.deleteMetricVersionMetasByLegacyTimeline(legacyTimeline, limit));

    return metricDeletedCount + metricVersionDeletedCount;
  }

  /**
   * 列出指标的所有版本
   *
   * @param metricIdent 指标标识符
   * @return 版本实体列表
   */
  public List<MetricVersionEntity> listMetricVersions(NameIdentifier metricIdent) {
    NameIdentifierUtil.checkMetric(metricIdent);

    MetricEntity metricEntity = getMetricByIdentifier(metricIdent);

    List<MetricVersionPO> versionPOs =
        SessionUtils.getWithoutCommit(
            MetricVersionMetaMapper.class,
            mapper -> mapper.listMetricVersionMetasByMetricId(metricEntity.id()));

    return versionPOs.stream()
        .filter(po -> po != null)
        .map(po -> POConverters.fromMetricVersionPO(po, metricIdent))
        .collect(Collectors.toList());
  }

  /**
   * 获取指定版本的指标版本
   *
   * @param metricIdent 指标标识符
   * @param version 版本号
   * @return 版本实体
   */
  public MetricVersionEntity getMetricVersion(NameIdentifier metricIdent, int version) {
    NameIdentifierUtil.checkMetric(metricIdent);

    MetricEntity metricEntity = getMetricByIdentifier(metricIdent);

    MetricVersionPO versionPO =
        SessionUtils.getWithoutCommit(
            MetricVersionMetaMapper.class,
            mapper -> mapper.selectMetricVersionMeta(metricEntity.id(), version));

    if (versionPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.METRIC_VERSION.name().toLowerCase(Locale.ROOT),
          metricIdent + " version " + version);
    }

    return POConverters.fromMetricVersionPO(versionPO, metricIdent);
  }

  /**
   * 删除指定版本
   *
   * @param metricIdent 指标标识符
   * @param version 版本号
   * @return 是否删除成功
   */
  public boolean deleteMetricVersion(NameIdentifier metricIdent, int version) {
    NameIdentifierUtil.checkMetric(metricIdent);

    MetricEntity metricEntity;
    try {
      metricEntity = getMetricByIdentifier(metricIdent);
    } catch (NoSuchEntityException e) {
      LOG.warn("Failed to delete metric version: {} version {}", metricIdent, version, e);
      return false;
    }

    Integer deletedCount =
        SessionUtils.doWithCommitAndFetchResult(
            MetricVersionMetaMapper.class,
            mapper ->
                mapper.softDeleteMetricVersionMetaByMetricIdAndVersion(metricEntity.id(), version));

    return deletedCount != null && deletedCount > 0;
  }

  /**
   * 更新指标版本
   *
   * @param metricIdent 指标标识符
   * @param version 版本号
   * @param updater 更新函数
   * @param <E> 实体类型
   * @return 更新后的版本实体
   * @throws IOException 如果更新失败
   */
  public <E extends Entity & HasIdentifier> MetricVersionEntity updateMetricVersion(
      NameIdentifier metricIdent, int version, Function<E, E> updater) throws IOException {
    NameIdentifierUtil.checkMetric(metricIdent);

    MetricEntity metricEntity = getMetricByIdentifier(metricIdent);

    MetricVersionPO oldVersionPO =
        SessionUtils.getWithoutCommit(
            MetricVersionMetaMapper.class,
            mapper -> mapper.selectMetricVersionMeta(metricEntity.id(), version));

    if (oldVersionPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.METRIC_VERSION.name().toLowerCase(Locale.ROOT),
          metricIdent + " version " + version);
    }

    MetricVersionEntity oldVersionEntity =
        POConverters.fromMetricVersionPO(oldVersionPO, metricIdent);
    MetricVersionEntity newVersionEntity =
        (MetricVersionEntity) updater.apply((E) oldVersionEntity);

    Preconditions.checkArgument(
        Objects.equals(oldVersionEntity.version(), newVersionEntity.version()),
        "The updated metric version: %s should be same with the version before: %s",
        newVersionEntity.version(),
        oldVersionEntity.version());

    Integer updateResult;
    try {
      updateResult =
          SessionUtils.doWithCommitAndFetchResult(
              MetricVersionMetaMapper.class,
              mapper ->
                  mapper.updateMetricVersionMeta(
                      POConverters.updateMetricVersionPO(oldVersionPO, newVersionEntity),
                      oldVersionPO));
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re,
          Entity.EntityType.METRIC_VERSION,
          metricIdent + " version " + newVersionEntity.version());
      throw re;
    }

    if (updateResult > 0) {
      return newVersionEntity;
    } else {
      throw new IOException("Failed to update the metric version: " + metricIdent + " v" + version);
    }
  }

  /**
   * 根据 schema ID 和指标编码获取指标 ID
   *
   * @param schemaId schema ID
   * @param metricCode 指标编码
   * @return 指标 ID
   */
  Long getMetricIdBySchemaIdAndMetricCode(Long schemaId, String metricCode) {
    Long metricId =
        SessionUtils.getWithoutCommit(
            MetricMetaMapper.class,
            mapper -> mapper.selectMetricIdBySchemaIdAndMetricCode(schemaId, metricCode));

    if (metricId == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.METRIC.name().toLowerCase(Locale.ROOT),
          metricCode);
    }

    return metricId;
  }

  /**
   * 根据标识符获取指标 PO
   *
   * @param ident 指标标识符
   * @return 指标 PO
   */
  MetricPO getMetricPOByIdentifier(NameIdentifier ident) {
    NameIdentifierUtil.checkMetric(ident);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());

    MetricPO metricPO =
        SessionUtils.getWithoutCommit(
            MetricMetaMapper.class,
            mapper -> mapper.selectMetricMetaBySchemaIdAndMetricCode(schemaId, ident.name()));

    if (metricPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.METRIC.name().toLowerCase(Locale.ROOT),
          ident.toString());
    }

    return metricPO;
  }

  /**
   * 填充指标 PO Builder 的父实体 ID
   *
   * @param builder PO Builder
   * @param ns namespace
   */
  private void fillMetricPOBuilderParentEntityId(MetricPO.Builder builder, Namespace ns) {
    NamespaceUtil.checkMetric(ns);
    Long[] parentEntityIds = CommonMetaService.getInstance().getParentEntityIdsByNamespace(ns);
    builder.withMetalakeId(parentEntityIds[0]);
    builder.withCatalogId(parentEntityIds[1]);
    builder.withSchemaId(parentEntityIds[2]);
  }

  /**
   * 创建新的指标版本（版本号自动递增）
   *
   * @param metricIdent 指标标识符
   * @param comment 版本注释
   * @param unit 单位
   * @param aggregationLogic 聚合逻辑
   * @param parentMetricIds 父指标IDs
   * @param calculationFormula 计算公式
   * @return 新创建的版本实体
   * @throws IOException 如果创建失败
   */
  public MetricVersionEntity createNewMetricVersion(
      NameIdentifier metricIdent,
      String comment,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula)
      throws IOException {
    NameIdentifierUtil.checkMetric(metricIdent);

    MetricPO metricPO = getMetricPOByIdentifier(metricIdent);
    MetricEntity metricEntity = POConverters.fromMetricPO(metricPO, metricIdent.namespace());

    // 创建版本实体（版本号在 SQL 层从 metric_meta.last_version 获取）
    MetricVersionEntity newVersionEntity =
        MetricVersionEntity.builder()
            .withMetricIdentifier(metricIdent)
            .withVersion(0) // 占位符，实际版本号由 SQL 层获取
            .withMetricName(metricEntity.name())
            .withMetricCode(metricEntity.code())
            .withMetricType(metricEntity.metricType())
            .withDataType(metricEntity.dataType())
            .withComment(comment)
            .withUnit(unit)
            .withAggregationLogic(aggregationLogic)
            .withParentMetricIds(parentMetricIds)
            .withCalculationFormula(calculationFormula)
            .withProperties(metricEntity.properties())
            .withAuditInfo(
                AuditInfo.builder()
                    .withCreator(PrincipalUtils.getCurrentPrincipal().getName())
                    .withCreateTime(Instant.now())
                    .build())
            .build();

    MetricVersionPO newVersionPO =
        POConverters.initializeMetricVersionPO(
            newVersionEntity,
            metricEntity.id(),
            metricPO.getMetalakeId(),
            metricPO.getCatalogId(),
            metricPO.getSchemaId());

    try {
      // 先原子递增 last_version，再插入版本记录（版本号从 metric_meta 实时获取）
      SessionUtils.doMultipleWithCommit(
          () ->
              SessionUtils.doWithoutCommit(
                  MetricMetaMapper.class,
                  mapper -> mapper.updateMetricLastVersion(metricEntity.id())),
          () ->
              SessionUtils.doWithoutCommit(
                  MetricVersionMetaMapper.class,
                  mapper -> mapper.insertMetricVersionMetaWithAutoVersion(newVersionPO)));
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.METRIC_VERSION, metricIdent.toString());
      throw re;
    }

    // 重新获取指标信息以获取实际的版本号
    MetricPO updatedMetricPO = getMetricPOByIdentifier(metricIdent);
    return MetricVersionEntity.builder()
        .withMetricIdentifier(metricIdent)
        .withVersion(updatedMetricPO.getLastVersion())
        .withMetricName(newVersionEntity.metricName())
        .withMetricCode(newVersionEntity.metricCode())
        .withMetricType(newVersionEntity.metricType())
        .withDataType(newVersionEntity.dataType())
        .withComment(newVersionEntity.comment())
        .withUnit(newVersionEntity.unit())
        .withAggregationLogic(newVersionEntity.aggregationLogic())
        .withParentMetricIds(newVersionEntity.parentMetricIds())
        .withCalculationFormula(newVersionEntity.calculationFormula())
        .withProperties(newVersionEntity.properties())
        .withAuditInfo(newVersionEntity.auditInfo())
        .build();
  }

  /**
   * 更新指定版本的信息（直接参数版本）
   *
   * @param metricIdent 指标标识符
   * @param version 版本号
   * @param comment 版本注释
   * @param unit 单位
   * @param aggregationLogic 聚合逻辑
   * @param parentMetricIds 父指标IDs
   * @param calculationFormula 计算公式
   * @return 更新后的版本实体
   * @throws IOException 如果更新失败
   */
  public MetricVersionEntity updateMetricVersion(
      NameIdentifier metricIdent,
      int version,
      String comment,
      String unit,
      String aggregationLogic,
      Long[] parentMetricIds,
      String calculationFormula)
      throws IOException {
    NameIdentifierUtil.checkMetric(metricIdent);

    MetricEntity metricEntity = getMetricByIdentifier(metricIdent);

    MetricVersionPO oldVersionPO =
        SessionUtils.getWithoutCommit(
            MetricVersionMetaMapper.class,
            mapper -> mapper.selectMetricVersionMeta(metricEntity.id(), version));

    if (oldVersionPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.METRIC_VERSION.name().toLowerCase(Locale.ROOT),
          metricIdent + " version " + version);
    }

    // 创建更新后的版本实体
    MetricVersionEntity newVersionEntity =
        MetricVersionEntity.builder()
            .withMetricIdentifier(metricIdent)
            .withVersion(version)
            .withMetricName(oldVersionPO.getMetricName())
            .withMetricCode(oldVersionPO.getMetricCode())
            .withMetricType(Metric.Type.valueOf(oldVersionPO.getMetricType()))
            .withDataType(oldVersionPO.getDataType())
            .withComment(comment)
            .withUnit(unit)
            .withAggregationLogic(aggregationLogic)
            .withParentMetricIds(parentMetricIds)
            .withCalculationFormula(calculationFormula)
            .withProperties(
                POConverters.fromMetricVersionPO(oldVersionPO, metricIdent).properties())
            .withAuditInfo(
                AuditInfo.builder()
                    .withCreator(
                        POConverters.fromMetricVersionPO(oldVersionPO, metricIdent)
                            .auditInfo()
                            .creator())
                    .withCreateTime(
                        POConverters.fromMetricVersionPO(oldVersionPO, metricIdent)
                            .auditInfo()
                            .createTime())
                    .withLastModifier(PrincipalUtils.getCurrentPrincipal().getName())
                    .withLastModifiedTime(Instant.now())
                    .build())
            .build();

    Integer updateResult;
    try {
      updateResult =
          SessionUtils.doWithCommitAndFetchResult(
              MetricVersionMetaMapper.class,
              mapper ->
                  mapper.updateMetricVersionMeta(
                      POConverters.updateMetricVersionPO(oldVersionPO, newVersionEntity),
                      oldVersionPO));
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.METRIC_VERSION, metricIdent + " version " + version);
      throw re;
    }

    if (updateResult > 0) {
      return newVersionEntity;
    } else {
      throw new IOException("Failed to update the metric version: " + metricIdent + " v" + version);
    }
  }

  /**
   * 根据指标 IDs 获取对应的 codes
   *
   * @param metricIds 指标 ID 列表
   * @return 对应的 code 数组，顺序与输入 IDs 一致
   */
  public String[] getMetricCodesByIds(Long[] metricIds) {
    if (metricIds == null || metricIds.length == 0) {
      return new String[0];
    }

    List<Long> idList = java.util.Arrays.asList(metricIds);
    List<MetricPO> metricPOs =
        SessionUtils.getWithoutCommit(
            MetricMetaMapper.class, mapper -> mapper.listMetricPOsByMetricIds(idList));

    // 构建 ID -> code 的映射
    java.util.Map<Long, String> idToCode = new java.util.HashMap<>();
    for (MetricPO po : metricPOs) {
      idToCode.put(po.getMetricId(), po.getMetricCode());
    }

    // 按原顺序返回 codes
    String[] codes = new String[metricIds.length];
    for (int i = 0; i < metricIds.length; i++) {
      codes[i] = idToCode.get(metricIds[i]);
    }
    return codes;
  }
}
