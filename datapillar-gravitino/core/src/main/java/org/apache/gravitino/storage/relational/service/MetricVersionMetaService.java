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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.exceptions.NoSuchEntityException;
import org.apache.gravitino.meta.MetricEntity;
import org.apache.gravitino.meta.MetricVersionEntity;
import org.apache.gravitino.storage.relational.mapper.MetricVersionMetaMapper;
import org.apache.gravitino.storage.relational.po.CatalogPO;
import org.apache.gravitino.storage.relational.po.MetalakePO;
import org.apache.gravitino.storage.relational.po.MetricVersionPO;
import org.apache.gravitino.storage.relational.po.SchemaPO;
import org.apache.gravitino.storage.relational.utils.POConverters;
import org.apache.gravitino.storage.relational.utils.SessionUtils;
import org.apache.gravitino.utils.NamespaceUtil;

/** MetricVersion metadata service */
public class MetricVersionMetaService {

  private static final MetricVersionMetaService INSTANCE = new MetricVersionMetaService();

  public static MetricVersionMetaService getInstance() {
    return INSTANCE;
  }

  private MetricVersionMetaService() {}

  /**
   * According to namespace List all indicator versions
   *
   * @param ns namespace (Format:[metalake,catalog,schema,metric_code])
   * @return Version entity list
   */
  public List<MetricVersionEntity> listVersionsByNamespace(Namespace ns) {
    NamespaceUtil.checkMetricVersion(ns); // namespace of all levels constitute metric identifier
    NameIdentifier metricIdent =
        NameIdentifier.of(
            ns.levels()); // Get metric entity,Will throw if it does not exist NoSuchEntityException
    MetricEntity metricEntity =
        MetricMetaService.getInstance()
            .getMetricByIdentifier(metricIdent); // Query the metric All versions of
    List<MetricVersionPO> versionPOs =
        SessionUtils.getWithoutCommit(
            MetricVersionMetaMapper.class,
            mapper -> mapper.listMetricVersionMetasByMetricId(metricEntity.id()));
    if (versionPOs.isEmpty()) {
      return List.of();
    }

    // Convert to Entity
    return POConverters.fromMetricVersionPOs(versionPOs, metricIdent);
  }

  /**
   * According to metric_id and version Get a specific version
   *
   * @param metricIdent indicator identifier
   * @param version version number
   * @return version entity
   * @throws NoSuchEntityException if the version does not exist
   */
  public MetricVersionEntity getVersionByIdentifier(NameIdentifier metricIdent, int version)
      throws NoSuchEntityException {

    // Get metric entity
    MetricEntity metricEntity =
        MetricMetaService.getInstance()
            .getMetricByIdentifier(metricIdent); // Query a specific version
    MetricVersionPO versionPO =
        SessionUtils.getWithoutCommit(
            MetricVersionMetaMapper.class,
            mapper ->
                mapper.selectMetricVersionMetaByMetricIdAndVersion(metricEntity.id(), version));
    if (versionPO == null) {
      throw new NoSuchEntityException(
          "MetricVersion %s (version %d) does not exist", metricIdent, version);
    }

    return POConverters.fromMetricVersionPO(versionPO, metricIdent);
  }

  /**
   * According to NameIdentifier Get version(Compatible JDBCBackend.get() call)
   *
   * @param ident version identifier(namespace = [metalake,catalog,schema,metric_code],name =
   *     version)
   * @return version entity
   * @throws NoSuchEntityException if the version does not exist
   */
  public MetricVersionEntity getMetricVersionByIdentifier(NameIdentifier ident)
      throws NoSuchEntityException {
    NamespaceUtil.checkMetricVersion(
        ident.namespace()); // namespace of all levels constitute metric identifier
    NameIdentifier metricIdent =
        NameIdentifier.of(ident.namespace().levels()); // ident.name() is the version number
    int version;
    try {
      version = Integer.parseInt(ident.name());
    } catch (NumberFormatException e) {
      throw new NoSuchEntityException("Invalid version number:%s,must be an integer", ident.name());
    }

    return getVersionByIdentifier(metricIdent, version);
  }

  public boolean deleteMetricVersionByIdentifier(NameIdentifier ident) {
    MetricVersionEntity versionEntity = getMetricVersionByIdentifier(ident);
    Integer deleteResult =
        SessionUtils.doWithCommitAndFetchResult(
            MetricVersionMetaMapper.class,
            mapper -> mapper.softDeleteMetricVersionMetaById(versionEntity.id()));
    return deleteResult != null && deleteResult > 0;
  }

  /**
   * According to the reference table ID Get current version metrics(Return only current_version
   * Corresponding record).*
   *
   * @param refTableId Reference table ID
   * @return Indicator version entity list
   */
  public List<MetricVersionEntity> listCurrentVersionsByRefTableId(Long refTableId) {
    if (refTableId == null) {
      return List.of();
    }

    List<MetricVersionPO> versionPOs =
        SessionUtils.getWithoutCommit(
            MetricVersionMetaMapper.class,
            mapper -> mapper.listMetricVersionMetasByRefTableId(refTableId));
    if (versionPOs.isEmpty()) {
      return List.of();
    }

    Map<Long, SchemaPO> schemaCache = new HashMap<>();
    Map<Long, CatalogPO> catalogCache = new HashMap<>();
    Map<Long, MetalakePO> metalakeCache = new HashMap<>();
    List<MetricVersionEntity> versions = new ArrayList<>(versionPOs.size());
    for (MetricVersionPO versionPO : versionPOs) {
      if (versionPO == null || versionPO.getMetricCode() == null) {
        continue;
      }

      SchemaPO schemaPO =
          schemaCache.computeIfAbsent(
              versionPO.getSchemaId(), id -> SchemaMetaService.getInstance().getSchemaPOById(id));
      if (schemaPO == null) {
        continue;
      }

      CatalogPO catalogPO =
          catalogCache.computeIfAbsent(
              schemaPO.getCatalogId(), id -> CatalogMetaService.getInstance().getCatalogPOById(id));
      if (catalogPO == null) {
        continue;
      }

      MetalakePO metalakePO =
          metalakeCache.computeIfAbsent(
              catalogPO.getMetalakeId(),
              id -> MetalakeMetaService.getInstance().getMetalakePOById(id));
      if (metalakePO == null) {
        continue;
      }

      NameIdentifier metricIdent =
          NameIdentifier.of(
              Namespace.of(
                  metalakePO.getMetalakeName(),
                  catalogPO.getCatalogName(),
                  schemaPO.getSchemaName()),
              versionPO.getMetricCode());
      versions.add(POConverters.fromMetricVersionPO(versionPO, metricIdent));
    }

    return List.copyOf(versions);
  }
}
