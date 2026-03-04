/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.The ASF licenses this file
 * to you under the Apache License,Version 2.0 (the
 * "License");you may not use this file except in compliance
 * with the License.You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,* software distributed under the License is distributed on an
 * "AS IS" BASIS,WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND,either express or implied.See the License for the
 * specific language governing permissions and limitations
 * under the License.*/
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

/** Service class processing Metric and MetricVersion database operations */
public class MetricMetaService {

  private static final Logger LOG = LoggerFactory.getLogger(MetricMetaService.class);
  private static final MetricMetaService INSTANCE = new MetricMetaService();

  public static MetricMetaService getInstance() {
    return INSTANCE;
  }

  private MetricMetaService() {}

  /**
   * According to namespace List all indicators
   *
   * @param ns namespace (should be schema Level)
   * @return Indicator entity list
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
   * List metrics in pages
   *
   * @param ns namespace (should be schema Level)
   * @param offset offset
   * @param limit page size
   * @return Indicator entity list
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
   * Total number of statistical indicators
   *
   * @param ns namespace (should be schema Level)
   * @return Total number of indicators
   */
  public long countMetricsByNamespace(Namespace ns) {
    NamespaceUtil.checkMetric(ns);
    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ns);
    return SessionUtils.getWithoutCommit(
        MetricMetaMapper.class, mapper -> mapper.countMetricsBySchemaId(schemaId));
  }

  /**
   * Get metrics based on identifier
   *
   * @param ident indicator NameIdentifier
   * @return indicator entity
   */
  public MetricEntity getMetricByIdentifier(NameIdentifier ident) {
    MetricPO metricPO = getMetricPOByIdentifier(ident);
    return POConverters.fromMetricPO(metricPO, ident.namespace());
  }

  /**
   * Insert new indicator
   *
   * @param metricEntity indicator entity
   * @param overwrite Whether to overwrite existing indicators
   * @throws IOException If insert fails
   */
  public void insertMetric(MetricEntity metricEntity, boolean overwrite) throws IOException {
    insertMetricWithVersion(metricEntity, overwrite, null, null, null, null, null, null);
  }

  /**
   * Insert new indicator and set properties of initial version
   *
   * @param metricEntity indicator entity
   * @param unit unit
   * @param parentMetricCodes Parent indicator encoding array
   * @param calculationFormula Calculation formula
   * @param refTableId quotedTable ID
   * @param measureColumnIds measure columnID JSONarray
   * @param filterColumnIds Filter columnsID JSONarray
   * @throws IOException If insert fails
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
   * Insert new indicator and set properties of initial version(internal method)
   *
   * @param metricEntity indicator entity
   * @param overwrite Whether to cover
   * @param unit unit
   * @param parentMetricCodes Parent indicator encoding array
   * @param calculationFormula Calculation formula
   * @param refTableId quotedTable ID
   * @param measureColumnIds measure columnID JSONarray
   * @param filterColumnIds Filter columnsID JSONarray
   * @throws IOException If insert fails
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
            // Also insert the first version into metric_version_info table,And set version related
            // properties
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
                    1); // The initial version number is 1
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
   * Update indicator(Update only descriptive fields:name,comment,Do not create version)
   *
   * <p>Version creation should be done via updateMetricVersion explicit call
   *
   * @param identifier indicator identifier
   * @param updater update function
   * @param <E> Entity type
   * @return Updated indicator entity
   * @throws IOException If the update fails
   */
  public <E extends Entity & HasIdentifier> MetricEntity updateMetric(
      NameIdentifier identifier, Function<E, E> updater) throws IOException {
    NameIdentifierUtil.checkMetric(identifier);
    MetricPO oldMetricPO = getMetricPOByIdentifier(identifier);
    MetricEntity oldMetricEntity = POConverters.fromMetricPO(oldMetricPO, identifier.namespace());
    MetricEntity newEntity = (MetricEntity) updater.apply((E) oldMetricEntity);
    Preconditions.checkArgument(
        Objects.equals(oldMetricEntity.id(), newEntity.id()),
        "The updated metric entity id:%s should be same with the metric entity id before:%s",
        newEntity.id(),
        oldMetricEntity.id());
    Integer updateResult;
    try {
      // Update only metric_meta table descriptive fields,Do not create version
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
      throw new IOException("Failed to update the entity:" + identifier);
    }
  }

  /**
   * Delete indicator
   *
   * @param ident indicator identifier
   * @return Is deletion successful?
   */
  public boolean deleteMetric(NameIdentifier ident) {
    NameIdentifierUtil.checkMetric(ident);
    Long schemaId;
    String metricCode = ident.name();
    try {
      schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    } catch (NoSuchEntityException e) {
      LOG.warn("Failed to delete metric:{}", ident, e);
      return false;
    }

    int[] metricDeletedCount = new int[] {0};
    int[] metricVersionDeletedCount =
        new int[] {0}; // Delete simultaneously metric_meta and metric_version_info table records
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
   * Switch the current version of the indicator(Update database directly,Does not trigger version
   * creation logic)
   *
   * @param ident indicator identifier
   * @param targetVersion Target version number
   * @return Updated indicator entity
   * @throws IOException If the update fails
   */
  public MetricVersionEntity switchMetricCurrentVersion(NameIdentifier ident, Integer targetVersion)
      throws IOException {
    NameIdentifierUtil.checkMetric(ident);
    MetricPO oldMetricPO = getMetricPOByIdentifier(ident); // Get target version details
    NameIdentifier versionIdent =
        NameIdentifier.of(NamespaceUtil.toMetricVersionNs(ident), String.valueOf(targetVersion));
    MetricVersionEntity targetVersionEntity =
        MetricVersionMetaService.getInstance()
            .getMetricVersionByIdentifier(
                versionIdent); // Update the main table with the target version of data
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
      throw new IOException("Failed to switch metric version:" + ident);
    }
  }

  /**
   * Remove metric metadata based on legacy timeline
   *
   * @param legacyTimeline legacy timeline
   * @param limit Remove quantity limit
   * @return Number of records deleted
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
   * List all versions of the indicator
   *
   * @param metricIdent indicator identifier
   * @return Version entity list
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
   * Get the indicator version of the specified version
   *
   * @param metricIdent indicator identifier
   * @param versionId versionID
   * @return version entity
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
   * Delete specified version
   *
   * @param metricIdent indicator identifier
   * @param versionId versionID
   * @return Is deletion successful?
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
   * Update indicator version(Create new version)
   *
   * <p>This method will create a new version,The version number is lastVersion + 1
   *
   * @param metricIdent indicator identifier
   * @param currentVersion Current version number(for verification,A new version will actually be
   *     created)
   * @param metricName Indicator name
   * @param metricCode Indicator coding
   * @param metricType Indicator type
   * @param dataType data type
   * @param comment Comment
   * @param unit unit
   * @param parentMetricCodes Parent indicator encoding array
   * @param calculationFormula Calculation formula
   * @param refTableId quotedTable ID
   * @param measureColumnIds measure columnID JSONarray
   * @param filterColumnIds Filter columnsID JSONarray
   * @return Newly created version entity
   * @throws IOException If creation fails
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
    // call createMetricVersion Create new version
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
   * Update indicator version
   *
   * @param metricIdent indicator identifier
   * @param versionId versionID
   * @param updater update function
   * @param <E> Entity type
   * @return Updated version entity
   * @throws IOException If the update fails
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
          "Failed to update the metric version:" + metricIdent + " id " + versionId);
    }
  }

  /**
   * According to schema ID and indicator encoding to obtain indicators ID
   *
   * @param schemaId schema ID
   * @param metricCode Indicator coding
   * @return indicator ID
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
   * Get metrics based on identifier PO
   *
   * @param ident indicator identifier
   * @return indicator PO
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
   * fill indicator PO Builder parent entity of ID
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
   * Create new version and update main table
   *
   * @param metricIdent indicator identifier
   * @param metricName Indicator name
   * @param metricCode Indicator coding
   * @param metricType Indicator type
   * @param dataType data type
   * @param comment Release Notes
   * @param unit unit
   * @param parentMetricCodes Parent indicator encoding array
   * @param calculationFormula Calculation formula
   * @param refTableId quotedTable ID
   * @param measureColumnIds measure columnID JSONarray
   * @param filterColumnIds Filter columnsID JSONarray
   * @return Newly created version entity
   * @throws IOException If creation fails
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
    MetricEntity metricEntity =
        POConverters.fromMetricPO(
            metricPO,
            metricIdent
                .namespace()); // Calculate new version number(Need to create entity before,because
    // version is a required field)
    Integer newVersion = metricPO.getLastVersion() + 1; // Create new version entity
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
            newVersion); // Build the updated main table PO
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
      // Insert new version record first
      SessionUtils.doWithCommit(
          MetricVersionMetaMapper.class,
          mapper ->
              mapper.insertMetricVersionMeta(
                  newVersionPO)); // Update the main tablecurrent_versionandlast_version
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
