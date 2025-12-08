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
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.apache.gravitino.Entity;
import org.apache.gravitino.HasIdentifier;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.Namespace;
import org.apache.gravitino.exceptions.NoSuchEntityException;
import org.apache.gravitino.meta.MetricRootEntity;
import org.apache.gravitino.storage.relational.mapper.MetricRootMetaMapper;
import org.apache.gravitino.storage.relational.po.MetricRootPO;
import org.apache.gravitino.storage.relational.utils.ExceptionUtils;
import org.apache.gravitino.storage.relational.utils.POConverters;
import org.apache.gravitino.storage.relational.utils.SessionUtils;
import org.apache.gravitino.utils.NameIdentifierUtil;
import org.apache.gravitino.utils.NamespaceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** MetricRoot 元数据服务类 */
public class MetricRootMetaService {
  private static final Logger LOG = LoggerFactory.getLogger(MetricRootMetaService.class);
  private static final MetricRootMetaService INSTANCE = new MetricRootMetaService();

  public static MetricRootMetaService getInstance() {
    return INSTANCE;
  }

  private MetricRootMetaService() {}

  /** 插入 Root */
  public void insertRoot(MetricRootEntity rootEntity, boolean overwrite) throws IOException {
    try {
      NameIdentifierUtil.checkRoot(rootEntity.nameIdentifier());

      MetricRootPO.Builder builder = MetricRootPO.builder();
      fillRootPOBuilderParentEntityId(builder, rootEntity.namespace());

      SessionUtils.doWithCommit(
          MetricRootMetaMapper.class,
          mapper -> {
            MetricRootPO po = POConverters.initializeMetricRootPO(rootEntity, builder);
            if (overwrite) {
              mapper.insertMetricRootMetaOnDuplicateKeyUpdate(po);
            } else {
              mapper.insertMetricRootMeta(po);
            }
          });
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.METRIC_ROOT, rootEntity.nameIdentifier().toString());
      throw re;
    }
  }

  /** 根据 namespace 列出所有 Root */
  public List<MetricRootEntity> listRootsByNamespace(Namespace namespace) {
    NamespaceUtil.checkRoot(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);
    LOG.info("DEBUG: listRootsByNamespace - namespace={}, schemaId={}", namespace, schemaId);

    List<MetricRootPO> rootPOs =
        SessionUtils.getWithoutCommit(
            MetricRootMetaMapper.class, mapper -> mapper.listMetricRootPOsBySchemaId(schemaId));
    LOG.info("DEBUG: listRootsByNamespace - 查询到 {} 个 RootPO", rootPOs.size());

    List<MetricRootEntity> result = POConverters.fromMetricRootPOs(rootPOs, namespace);
    LOG.info("DEBUG: listRootsByNamespace - 转换后返回 {} 个 Entity", result.size());
    return result;
  }

  /** 获取 Root */
  public MetricRootEntity getRootByIdentifier(NameIdentifier ident) {
    NameIdentifierUtil.checkRoot(ident);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    MetricRootPO rootPO = getRootPOBySchemaIdAndCode(schemaId, ident.name());

    return POConverters.fromMetricRootPO(rootPO, ident.namespace());
  }

  /** 更新 Root */
  public <E extends Entity & HasIdentifier> MetricRootEntity updateRoot(
      NameIdentifier ident, Function<E, E> updater) throws IOException {
    NameIdentifierUtil.checkRoot(ident);

    String rootCode = ident.name();
    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());

    MetricRootPO oldRootPO = getRootPOBySchemaIdAndCode(schemaId, rootCode);
    MetricRootEntity oldRootEntity = POConverters.fromMetricRootPO(oldRootPO, ident.namespace());
    MetricRootEntity newEntity = (MetricRootEntity) updater.apply((E) oldRootEntity);
    Preconditions.checkArgument(
        Objects.equals(oldRootEntity.id(), newEntity.id()),
        "The updated root entity id: %s should be same with the root entity id before: %s",
        newEntity.id(),
        oldRootEntity.id());

    Integer updateResult;
    try {
      updateResult =
          SessionUtils.doWithCommitAndFetchResult(
              MetricRootMetaMapper.class,
              mapper ->
                  mapper.updateMetricRootMeta(
                      POConverters.updateMetricRootPO(oldRootPO, newEntity), oldRootPO));
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.METRIC_ROOT, newEntity.nameIdentifier().toString());
      throw re;
    }

    if (updateResult > 0) {
      return newEntity;
    } else {
      throw new IOException("Failed to update the entity: " + ident);
    }
  }

  /** 删除 Root */
  public boolean deleteRoot(NameIdentifier ident) {
    NameIdentifierUtil.checkRoot(ident);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    String rootCode = ident.name();

    Integer deleteResult =
        SessionUtils.doWithCommitAndFetchResult(
            MetricRootMetaMapper.class,
            mapper -> mapper.softDeleteMetricRootMetaBySchemaIdAndRootCode(schemaId, rootCode));

    return deleteResult > 0;
  }

  private MetricRootPO getRootPOBySchemaIdAndCode(Long schemaId, String rootCode) {
    MetricRootPO rootPO =
        SessionUtils.getWithoutCommit(
            MetricRootMetaMapper.class,
            mapper -> mapper.selectMetricRootMetaBySchemaIdAndRootCode(schemaId, rootCode));

    if (rootPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.METRIC_ROOT.name().toLowerCase(),
          rootCode);
    }
    return rootPO;
  }

  private void fillRootPOBuilderParentEntityId(MetricRootPO.Builder builder, Namespace namespace) {
    NamespaceUtil.checkRoot(namespace);
    Long parentEntityId = null;
    for (int level = 0; level < namespace.levels().length; level++) {
      String name = namespace.level(level);
      switch (level) {
        case 0:
          parentEntityId = MetalakeMetaService.getInstance().getMetalakeIdByName(name);
          builder.withMetalakeId(parentEntityId);
          continue;
        case 1:
          parentEntityId =
              CatalogMetaService.getInstance()
                  .getCatalogIdByMetalakeIdAndName(parentEntityId, name);
          builder.withCatalogId(parentEntityId);
          continue;
        case 2:
          parentEntityId =
              SchemaMetaService.getInstance().getSchemaIdByCatalogIdAndName(parentEntityId, name);
          builder.withSchemaId(parentEntityId);
          continue;
        default:
          throw new IllegalArgumentException("Root namespace only supports 3 levels");
      }
    }
  }

  /** 根据遗留时间线删除词根元数据 */
  public int deleteMetricRootMetasByLegacyTimeline(Long legacyTimeline, int limit) {
    return SessionUtils.doWithCommitAndFetchResult(
        MetricRootMetaMapper.class,
        mapper -> mapper.deleteMetricRootMetasByLegacyTimeline(legacyTimeline, limit));
  }
}
