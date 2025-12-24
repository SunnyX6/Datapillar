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
import org.apache.gravitino.meta.MetricModifierEntity;
import org.apache.gravitino.storage.relational.mapper.MetricModifierMetaMapper;
import org.apache.gravitino.storage.relational.po.MetricModifierPO;
import org.apache.gravitino.storage.relational.utils.ExceptionUtils;
import org.apache.gravitino.storage.relational.utils.POConverters;
import org.apache.gravitino.storage.relational.utils.SessionUtils;
import org.apache.gravitino.utils.NameIdentifierUtil;
import org.apache.gravitino.utils.NamespaceUtil;

/** MetricModifier 元数据服务类 */
public class MetricModifierMetaService {
  private static final MetricModifierMetaService INSTANCE = new MetricModifierMetaService();

  public static MetricModifierMetaService getInstance() {
    return INSTANCE;
  }

  private MetricModifierMetaService() {}

  /** 插入 Modifier */
  public void insertModifier(MetricModifierEntity modifierEntity, boolean overwrite)
      throws IOException {
    try {
      NameIdentifierUtil.checkModifier(modifierEntity.nameIdentifier());

      MetricModifierPO.Builder builder = MetricModifierPO.builder();
      fillModifierPOBuilderParentEntityId(builder, modifierEntity.namespace());

      SessionUtils.doWithCommit(
          MetricModifierMetaMapper.class,
          mapper -> {
            MetricModifierPO po = POConverters.initializeMetricModifierPO(modifierEntity, builder);
            if (overwrite) {
              mapper.insertMetricModifierMetaOnDuplicateKeyUpdate(po);
            } else {
              mapper.insertMetricModifierMeta(po);
            }
          });
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.METRIC_MODIFIER, modifierEntity.nameIdentifier().toString());
      throw re;
    }
  }

  /** 根据 namespace 列出所有 Modifier */
  public List<MetricModifierEntity> listModifiersByNamespace(Namespace namespace) {
    NamespaceUtil.checkModifier(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    List<MetricModifierPO> modifierPOs =
        SessionUtils.getWithoutCommit(
            MetricModifierMetaMapper.class,
            mapper -> mapper.listMetricModifierPOsBySchemaId(schemaId));

    return POConverters.fromMetricModifierPOs(modifierPOs, namespace);
  }

  /** 分页列出 Modifier */
  public List<MetricModifierEntity> listModifiersByNamespaceWithPagination(
      Namespace namespace, int offset, int limit) {
    NamespaceUtil.checkModifier(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    List<MetricModifierPO> modifierPOs =
        SessionUtils.getWithoutCommit(
            MetricModifierMetaMapper.class,
            mapper ->
                mapper.listMetricModifierPOsBySchemaIdWithPagination(schemaId, offset, limit));

    return POConverters.fromMetricModifierPOs(modifierPOs, namespace);
  }

  /** 统计 Modifier 总数 */
  public long countModifiersByNamespace(Namespace namespace) {
    NamespaceUtil.checkModifier(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    return SessionUtils.getWithoutCommit(
        MetricModifierMetaMapper.class, mapper -> mapper.countMetricModifiersBySchemaId(schemaId));
  }

  /** 获取 Modifier */
  public MetricModifierEntity getModifierByIdentifier(NameIdentifier ident) {
    NameIdentifierUtil.checkModifier(ident);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    MetricModifierPO modifierPO = getModifierPOBySchemaIdAndCode(schemaId, ident.name());

    return POConverters.fromMetricModifierPO(modifierPO, ident.namespace());
  }

  /** 更新 Modifier */
  public <E extends Entity & HasIdentifier> MetricModifierEntity updateModifier(
      NameIdentifier ident, Function<E, E> updater) throws IOException {
    NameIdentifierUtil.checkModifier(ident);

    String modifierCode = ident.name();
    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());

    MetricModifierPO oldModifierPO = getModifierPOBySchemaIdAndCode(schemaId, modifierCode);
    MetricModifierEntity oldModifierEntity =
        POConverters.fromMetricModifierPO(oldModifierPO, ident.namespace());
    MetricModifierEntity newEntity = (MetricModifierEntity) updater.apply((E) oldModifierEntity);
    Preconditions.checkArgument(
        Objects.equals(oldModifierEntity.id(), newEntity.id()),
        "The updated modifier entity id: %s should be same with the modifier entity id before: %s",
        newEntity.id(),
        oldModifierEntity.id());

    Integer updateResult;
    try {
      updateResult =
          SessionUtils.doWithCommitAndFetchResult(
              MetricModifierMetaMapper.class,
              mapper ->
                  mapper.updateMetricModifierMeta(
                      POConverters.updateMetricModifierPO(oldModifierPO, newEntity),
                      oldModifierPO));
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.METRIC_MODIFIER, newEntity.nameIdentifier().toString());
      throw re;
    }

    if (updateResult > 0) {
      return newEntity;
    } else {
      throw new IOException("Failed to update the entity: " + ident);
    }
  }

  /** 删除 Modifier */
  public boolean deleteModifier(NameIdentifier ident) {
    NameIdentifierUtil.checkModifier(ident);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    String modifierCode = ident.name();

    Integer deleteResult =
        SessionUtils.doWithCommitAndFetchResult(
            MetricModifierMetaMapper.class,
            mapper ->
                mapper.softDeleteMetricModifierMetaBySchemaIdAndModifierCode(
                    schemaId, modifierCode));

    return deleteResult > 0;
  }

  private MetricModifierPO getModifierPOBySchemaIdAndCode(Long schemaId, String modifierCode) {
    MetricModifierPO modifierPO =
        SessionUtils.getWithoutCommit(
            MetricModifierMetaMapper.class,
            mapper ->
                mapper.selectMetricModifierMetaBySchemaIdAndModifierCode(schemaId, modifierCode));

    if (modifierPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.METRIC_MODIFIER.name().toLowerCase(),
          modifierCode);
    }
    return modifierPO;
  }

  private void fillModifierPOBuilderParentEntityId(
      MetricModifierPO.Builder builder, Namespace namespace) {
    NamespaceUtil.checkModifier(namespace);
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
          throw new IllegalArgumentException("Modifier namespace only supports 3 levels");
      }
    }
  }

  /** 根据遗留时间线删除修饰符元数据 */
  public int deleteMetricModifierMetasByLegacyTimeline(Long legacyTimeline, int limit) {
    return SessionUtils.doWithCommitAndFetchResult(
        MetricModifierMetaMapper.class,
        mapper -> mapper.deleteMetricModifierMetasByLegacyTimeline(legacyTimeline, limit));
  }
}
