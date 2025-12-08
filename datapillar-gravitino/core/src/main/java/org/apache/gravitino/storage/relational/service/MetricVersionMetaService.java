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

import java.util.Collections;
import java.util.List;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.exceptions.NoSuchEntityException;
import org.apache.gravitino.meta.MetricEntity;
import org.apache.gravitino.meta.MetricVersionEntity;
import org.apache.gravitino.storage.relational.mapper.MetricVersionMetaMapper;
import org.apache.gravitino.storage.relational.po.MetricVersionPO;
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
      return Collections.emptyList();
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
            mapper -> mapper.selectMetricVersionMeta(metricEntity.id(), version));

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
}
