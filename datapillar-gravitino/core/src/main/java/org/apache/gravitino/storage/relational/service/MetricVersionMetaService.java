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

/** MetricVersion 元数据服务 */
public class MetricVersionMetaService {

  private static final MetricVersionMetaService INSTANCE = new MetricVersionMetaService();

  public static MetricVersionMetaService getInstance() {
    return INSTANCE;
  }

  private MetricVersionMetaService() {}

  /**
   * 根据 namespace 列出所有指标版本
   *
   * @param ns namespace (格式: [metalake, catalog, schema, metric_code])
   * @return 版本实体列表
   */
  public List<MetricVersionEntity> listVersionsByNamespace(Namespace ns) {
    NamespaceUtil.checkMetricVersion(ns);

    // namespace 的所有 levels 构成 metric identifier
    NameIdentifier metricIdent = NameIdentifier.of(ns.levels());

    // 获取 metric entity，如果不存在会抛出 NoSuchEntityException
    MetricEntity metricEntity = MetricMetaService.getInstance().getMetricByIdentifier(metricIdent);

    // 查询该 metric 的所有版本
    List<MetricVersionPO> versionPOs =
        SessionUtils.getWithoutCommit(
            MetricVersionMetaMapper.class,
            mapper -> mapper.listMetricVersionMetasByMetricId(metricEntity.id()));

    if (versionPOs.isEmpty()) {
      return List.of();
    }

    // 转换为 Entity
    return POConverters.fromMetricVersionPOs(versionPOs, metricIdent);
  }

  /**
   * 根据 metric_id 和 version 获取特定版本
   *
   * @param metricIdent 指标标识符
   * @param version 版本号
   * @return 版本实体
   * @throws NoSuchEntityException 如果版本不存在
   */
  public MetricVersionEntity getVersionByIdentifier(NameIdentifier metricIdent, int version)
      throws NoSuchEntityException {

    // 获取 metric entity
    MetricEntity metricEntity = MetricMetaService.getInstance().getMetricByIdentifier(metricIdent);

    // 查询特定版本
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
   * 根据 NameIdentifier 获取版本（兼容 JDBCBackend.get() 调用）
   *
   * @param ident 版本标识符（namespace = [metalake, catalog, schema, metric_code], name = version）
   * @return 版本实体
   * @throws NoSuchEntityException 如果版本不存在
   */
  public MetricVersionEntity getMetricVersionByIdentifier(NameIdentifier ident)
      throws NoSuchEntityException {
    NamespaceUtil.checkMetricVersion(ident.namespace());

    // namespace 的所有 levels 构成 metric identifier
    NameIdentifier metricIdent = NameIdentifier.of(ident.namespace().levels());

    // ident.name() 是版本号
    int version;
    try {
      version = Integer.parseInt(ident.name());
    } catch (NumberFormatException e) {
      throw new NoSuchEntityException(
          "Invalid version number: %s, must be an integer", ident.name());
    }

    return getVersionByIdentifier(metricIdent, version);
  }

  /**
   * 根据引用表 ID 获取当前版本指标（仅返回 current_version 对应记录）。
   *
   * @param refTableId 引用表 ID
   * @return 指标版本实体列表
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
