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
    insertMetricWithVersion(metricEntity, overwrite, null, null, null, null, null, null);
  }

  /**
   * 插入新指标并设置初始版本的属性
   *
   * @param metricEntity 指标实体
   * @param unit 单位
   * @param parentMetricCodes 父指标编码数组
   * @param calculationFormula 计算公式
   * @param refTableId 引用的Table ID
   * @param measureColumnIds 度量列ID JSON数组
   * @param filterColumnIds 过滤列ID JSON数组
   * @throws IOException 如果插入失败
   */
  public void insertMetricWithVersion(
      MetricEntity metricEntity,
      String unit,
      String[] parentMetricCodes,
      String calculationFormula,
      Long refTableId,
      String measureColumnIds,
      String filterColumnIds)
      throws IOException {
    insertMetricWithVersion(
        metricEntity,
        false,
        unit,
        parentMetricCodes,
        calculationFormula,
        refTableId,
        measureColumnIds,
        filterColumnIds);
  }

  /**
   * 插入新指标并设置初始版本的属性（内部方法）
   *
   * @param metricEntity 指标实体
   * @param overwrite 是否覆盖
   * @param unit 单位
   * @param parentMetricCodes 父指标编码数组
   * @param calculationFormula 计算公式
   * @param refTableId 引用的Table ID
   * @param measureColumnIds 度量列ID JSON数组
   * @param filterColumnIds 过滤列ID JSON数组
   * @throws IOException 如果插入失败
   */
  private void insertMetricWithVersion(
      MetricEntity metricEntity,
      boolean overwrite,
      String unit,
      String[] parentMetricCodes,
      String calculationFormula,
      Long refTableId,
      String measureColumnIds,
      String filterColumnIds)
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
                    MetricPO po =
                        POConverters.initializeMetricPOWithVersion(metricEntity, builder, unit);
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
                    parentMetricCodes,
                    calculationFormula,
                    refTableId,
                    measureColumnIds,
                    filterColumnIds);
            MetricPO metricPO = builder.build();
            MetricVersionPO versionPO =
                POConverters.initializeMetricVersionPO(
                    initialVersion,
                    metricPO.getMetricId(),
                    metricPO.getMetalakeId(),
                    metricPO.getCatalogId(),
                    metricPO.getSchemaId(),
                    1); // 初始版本号为 1
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
   * <p>版本创建应通过 updateMetricVersion 显式调用
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
                      POConverters.updateMetricPOWithVersion(oldMetricPO, newEntity, null),
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
  public MetricVersionEntity switchMetricCurrentVersion(NameIdentifier ident, Integer targetVersion)
      throws IOException {
    NameIdentifierUtil.checkMetric(ident);

    MetricPO oldMetricPO = getMetricPOByIdentifier(ident);

    // 获取目标版本的详情
    NameIdentifier versionIdent =
        NameIdentifier.of(NamespaceUtil.toMetricVersionNs(ident), String.valueOf(targetVersion));
    MetricVersionEntity targetVersionEntity =
        MetricVersionMetaService.getInstance().getMetricVersionByIdentifier(versionIdent);

    // 用目标版本的数据更新主表
    MetricPO updatedPO =
        MetricPO.builder()
            .withMetricId(oldMetricPO.getMetricId())
            .withMetricName(targetVersionEntity.metricName())
            .withMetricCode(targetVersionEntity.metricCode())
            .withMetricType(targetVersionEntity.metricType().name())
            .withDataType(targetVersionEntity.dataType())
            .withUnit(targetVersionEntity.unit())
            .withMetalakeId(oldMetricPO.getMetalakeId())
            .withCatalogId(oldMetricPO.getCatalogId())
            .withSchemaId(oldMetricPO.getSchemaId())
            .withMetricComment(targetVersionEntity.comment())
            .withCurrentVersion(targetVersion)
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
      return targetVersionEntity;
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
   * @param versionId 版本ID
   * @return 版本实体
   */
  public MetricVersionEntity getMetricVersion(NameIdentifier metricIdent, Long versionId) {
    NameIdentifierUtil.checkMetric(metricIdent);

    MetricVersionPO versionPO =
        SessionUtils.getWithoutCommit(
            MetricVersionMetaMapper.class, mapper -> mapper.selectMetricVersionMetaById(versionId));

    if (versionPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.METRIC_VERSION.name().toLowerCase(Locale.ROOT),
          metricIdent + " version id " + versionId);
    }

    return POConverters.fromMetricVersionPO(versionPO, metricIdent);
  }

  /**
   * 删除指定版本
   *
   * @param metricIdent 指标标识符
   * @param versionId 版本ID
   * @return 是否删除成功
   */
  public boolean deleteMetricVersion(NameIdentifier metricIdent, Long versionId) {
    NameIdentifierUtil.checkMetric(metricIdent);

    Integer deletedCount =
        SessionUtils.doWithCommitAndFetchResult(
            MetricVersionMetaMapper.class,
            mapper -> mapper.softDeleteMetricVersionMetaById(versionId));

    return deletedCount != null && deletedCount > 0;
  }

  /**
   * 更新指标版本（创建新版本）
   *
   * <p>该方法会创建一个新版本，版本号为 lastVersion + 1
   *
   * @param metricIdent 指标标识符
   * @param currentVersion 当前版本号（用于验证，实际会创建新版本）
   * @param metricName 指标名称
   * @param metricCode 指标编码
   * @param metricType 指标类型
   * @param dataType 数据类型
   * @param comment 注释
   * @param unit 单位
   * @param parentMetricCodes 父指标编码数组
   * @param calculationFormula 计算公式
   * @param refTableId 引用的Table ID
   * @param measureColumnIds 度量列ID JSON数组
   * @param filterColumnIds 过滤列ID JSON数组
   * @return 新创建的版本实体
   * @throws IOException 如果创建失败
   */
  public MetricVersionEntity updateMetricVersion(
      NameIdentifier metricIdent,
      int currentVersion,
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
      throws IOException {
    // 调用 createMetricVersion 创建新版本
    return createMetricVersion(
        metricIdent,
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

  /**
   * 更新指标版本
   *
   * @param metricIdent 指标标识符
   * @param versionId 版本ID
   * @param updater 更新函数
   * @param <E> 实体类型
   * @return 更新后的版本实体
   * @throws IOException 如果更新失败
   */
  public <E extends Entity & HasIdentifier> MetricVersionEntity updateMetricVersion(
      NameIdentifier metricIdent, Long versionId, Function<E, E> updater) throws IOException {
    NameIdentifierUtil.checkMetric(metricIdent);

    MetricVersionPO oldVersionPO =
        SessionUtils.getWithoutCommit(
            MetricVersionMetaMapper.class, mapper -> mapper.selectMetricVersionMetaById(versionId));

    if (oldVersionPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.METRIC_VERSION.name().toLowerCase(Locale.ROOT),
          metricIdent + " version id " + versionId);
    }

    MetricVersionEntity oldVersionEntity =
        POConverters.fromMetricVersionPO(oldVersionPO, metricIdent);
    MetricVersionEntity newVersionEntity =
        (MetricVersionEntity) updater.apply((E) oldVersionEntity);

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
          re, Entity.EntityType.METRIC_VERSION, metricIdent + " version id " + versionId);
      throw re;
    }

    if (updateResult > 0) {
      return newVersionEntity;
    } else {
      throw new IOException(
          "Failed to update the metric version: " + metricIdent + " id " + versionId);
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
   * 创建新版本并更新主表
   *
   * @param metricIdent 指标标识符
   * @param metricName 指标名称
   * @param metricCode 指标编码
   * @param metricType 指标类型
   * @param dataType 数据类型
   * @param comment 版本注释
   * @param unit 单位
   * @param parentMetricCodes 父指标编码数组
   * @param calculationFormula 计算公式
   * @param refTableId 引用的Table ID
   * @param measureColumnIds 度量列ID JSON数组
   * @param filterColumnIds 过滤列ID JSON数组
   * @return 新创建的版本实体
   * @throws IOException 如果创建失败
   */
  public MetricVersionEntity createMetricVersion(
      NameIdentifier metricIdent,
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
      throws IOException {
    NameIdentifierUtil.checkMetric(metricIdent);

    MetricPO metricPO = getMetricPOByIdentifier(metricIdent);
    MetricEntity metricEntity = POConverters.fromMetricPO(metricPO, metricIdent.namespace());

    // 计算新版本号（需要在创建 entity 之前，因为 version 是必填字段）
    Integer newVersion = metricPO.getLastVersion() + 1;

    // 创建新版本实体
    MetricVersionEntity newVersionEntity =
        MetricVersionEntity.builder()
            .withMetricIdentifier(metricIdent)
            .withVersion(newVersion)
            .withMetricName(metricName)
            .withMetricCode(metricCode)
            .withMetricType(Metric.Type.valueOf(metricType))
            .withDataType(dataType)
            .withComment(comment)
            .withUnit(unit)
            .withUnitName(unitName)
            .withParentMetricCodes(parentMetricCodes)
            .withCalculationFormula(calculationFormula)
            .withRefTableId(refTableId)
            .withMeasureColumnIds(measureColumnIds)
            .withFilterColumnIds(filterColumnIds)
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
            metricPO.getSchemaId(),
            newVersion);

    // 构建更新后的主表 PO
    MetricPO updatedMetricPO =
        MetricPO.builder()
            .withMetricId(metricPO.getMetricId())
            .withMetricName(metricName)
            .withMetricCode(metricCode)
            .withMetricType(metricType)
            .withDataType(dataType)
            .withUnit(unit)
            .withMetalakeId(metricPO.getMetalakeId())
            .withCatalogId(metricPO.getCatalogId())
            .withSchemaId(metricPO.getSchemaId())
            .withMetricComment(comment)
            .withCurrentVersion(newVersion)
            .withLastVersion(newVersion)
            .withAuditInfo(metricPO.getAuditInfo())
            .withDeletedAt(metricPO.getDeletedAt())
            .build();

    try {
      // 先插入新版本记录
      SessionUtils.doWithCommit(
          MetricVersionMetaMapper.class, mapper -> mapper.insertMetricVersionMeta(newVersionPO));

      // 更新主表的current_version和last_version
      SessionUtils.doWithCommit(
          MetricMetaMapper.class, mapper -> mapper.updateMetricMeta(updatedMetricPO, metricPO));

    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.METRIC_VERSION, metricIdent.toString());
      throw re;
    }

    return MetricVersionEntity.builder()
        .withVersion(newVersion)
        .withMetricIdentifier(metricIdent)
        .withMetricName(newVersionEntity.metricName())
        .withMetricCode(newVersionEntity.metricCode())
        .withMetricType(newVersionEntity.metricType())
        .withDataType(newVersionEntity.dataType())
        .withComment(newVersionEntity.comment())
        .withUnit(newVersionEntity.unit())
        .withUnitName(newVersionEntity.unitName())
        .withParentMetricCodes(newVersionEntity.parentMetricCodes())
        .withCalculationFormula(newVersionEntity.calculationFormula())
        .withRefTableId(newVersionEntity.refTableId())
        .withMeasureColumnIds(newVersionEntity.measureColumnIds())
        .withFilterColumnIds(newVersionEntity.filterColumnIds())
        .withProperties(newVersionEntity.properties())
        .withAuditInfo(newVersionEntity.auditInfo())
        .build();
  }
}
