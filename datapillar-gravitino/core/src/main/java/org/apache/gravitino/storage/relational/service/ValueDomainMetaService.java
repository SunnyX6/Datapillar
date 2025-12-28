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
import org.apache.gravitino.meta.ValueDomainEntity;
import org.apache.gravitino.storage.relational.mapper.ValueDomainMetaMapper;
import org.apache.gravitino.storage.relational.po.ValueDomainPO;
import org.apache.gravitino.storage.relational.utils.ExceptionUtils;
import org.apache.gravitino.storage.relational.utils.POConverters;
import org.apache.gravitino.storage.relational.utils.SessionUtils;
import org.apache.gravitino.utils.NameIdentifierUtil;
import org.apache.gravitino.utils.NamespaceUtil;

/** ValueDomain 元数据服务类 */
public class ValueDomainMetaService {
  private static final ValueDomainMetaService INSTANCE = new ValueDomainMetaService();

  public static ValueDomainMetaService getInstance() {
    return INSTANCE;
  }

  private ValueDomainMetaService() {}

  /** 根据 schemaId 和 domainCode 获取值域的 domainId */
  public Long getValueDomainIdBySchemaIdAndDomainCode(Long schemaId, String domainCode) {
    ValueDomainPO domainPO =
        SessionUtils.getWithoutCommit(
            ValueDomainMetaMapper.class,
            mapper -> mapper.selectValueDomainMetaBySchemaIdAndDomainCode(schemaId, domainCode));

    if (domainPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.VALUE_DOMAIN.name().toLowerCase(),
          domainCode);
    }
    return domainPO.getDomainId();
  }

  /** 插入 ValueDomain */
  public void insertValueDomain(ValueDomainEntity entity, boolean overwrite) throws IOException {
    try {
      NameIdentifierUtil.checkValueDomain(entity.nameIdentifier());

      ValueDomainPO.Builder builder = ValueDomainPO.builder();
      fillValueDomainPOBuilderParentEntityId(builder, entity.namespace());

      SessionUtils.doWithCommit(
          ValueDomainMetaMapper.class,
          mapper -> {
            ValueDomainPO po = POConverters.initializeValueDomainPO(entity, builder);
            if (overwrite) {
              mapper.insertValueDomainMetaOnDuplicateKeyUpdate(po);
            } else {
              mapper.insertValueDomainMeta(po);
            }
          });
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.VALUE_DOMAIN, entity.nameIdentifier().toString());
      throw re;
    }
  }

  /** 根据 namespace 列出所有 ValueDomain */
  public List<ValueDomainEntity> listValueDomainsByNamespace(Namespace namespace) {
    NamespaceUtil.checkValueDomain(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    List<ValueDomainPO> domainPOs =
        SessionUtils.getWithoutCommit(
            ValueDomainMetaMapper.class, mapper -> mapper.listValueDomainPOsBySchemaId(schemaId));

    return POConverters.fromValueDomainPOs(domainPOs, namespace);
  }

  /** 分页列出 ValueDomain */
  public List<ValueDomainEntity> listValueDomainsByNamespaceWithPagination(
      Namespace namespace, int offset, int limit) {
    NamespaceUtil.checkValueDomain(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    List<ValueDomainPO> domainPOs =
        SessionUtils.getWithoutCommit(
            ValueDomainMetaMapper.class,
            mapper -> mapper.listValueDomainPOsBySchemaIdWithPagination(schemaId, offset, limit));

    return POConverters.fromValueDomainPOs(domainPOs, namespace);
  }

  /** 统计 ValueDomain 总数 */
  public long countValueDomainsByNamespace(Namespace namespace) {
    NamespaceUtil.checkValueDomain(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    return SessionUtils.getWithoutCommit(
        ValueDomainMetaMapper.class, mapper -> mapper.countValueDomainsBySchemaId(schemaId));
  }

  /** 获取 ValueDomain */
  public ValueDomainEntity getValueDomainByIdentifier(NameIdentifier ident) {
    NameIdentifierUtil.checkValueDomain(ident);

    String domainCode = ident.name();
    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    ValueDomainPO domainPO = getValueDomainPOBySchemaIdAndDomainCode(schemaId, domainCode);

    return POConverters.fromValueDomainPO(domainPO, ident.namespace());
  }

  /** 更新 ValueDomain */
  public <E extends Entity & HasIdentifier> ValueDomainEntity updateValueDomain(
      NameIdentifier ident, Function<E, E> updater) throws IOException {
    NameIdentifierUtil.checkValueDomain(ident);

    String domainCode = ident.name();
    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());

    ValueDomainPO oldPO = getValueDomainPOBySchemaIdAndDomainCode(schemaId, domainCode);
    ValueDomainEntity oldEntity = POConverters.fromValueDomainPO(oldPO, ident.namespace());
    ValueDomainEntity newEntity = (ValueDomainEntity) updater.apply((E) oldEntity);
    Preconditions.checkArgument(
        Objects.equals(oldEntity.id(), newEntity.id()),
        "The updated value domain entity id: %s should be same with the entity id before: %s",
        newEntity.id(),
        oldEntity.id());

    Integer updateResult;
    try {
      updateResult =
          SessionUtils.doWithCommitAndFetchResult(
              ValueDomainMetaMapper.class,
              mapper ->
                  mapper.updateValueDomainMeta(
                      POConverters.updateValueDomainPO(oldPO, newEntity), oldPO));
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.VALUE_DOMAIN, newEntity.nameIdentifier().toString());
      throw re;
    }

    if (updateResult > 0) {
      return newEntity;
    } else {
      throw new IOException("Failed to update the entity: " + ident);
    }
  }

  /** 删除 ValueDomain */
  public boolean deleteValueDomain(NameIdentifier ident) {
    NameIdentifierUtil.checkValueDomain(ident);

    String domainCode = ident.name();
    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());

    Integer deleteResult =
        SessionUtils.doWithCommitAndFetchResult(
            ValueDomainMetaMapper.class,
            mapper ->
                mapper.softDeleteValueDomainMetaBySchemaIdAndDomainCode(schemaId, domainCode));

    return deleteResult > 0;
  }

  private ValueDomainPO getValueDomainPOBySchemaIdAndDomainCode(Long schemaId, String domainCode) {
    ValueDomainPO domainPO =
        SessionUtils.getWithoutCommit(
            ValueDomainMetaMapper.class,
            mapper -> mapper.selectValueDomainMetaBySchemaIdAndDomainCode(schemaId, domainCode));

    if (domainPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.VALUE_DOMAIN.name().toLowerCase(),
          domainCode);
    }
    return domainPO;
  }

  private void fillValueDomainPOBuilderParentEntityId(
      ValueDomainPO.Builder builder, Namespace namespace) {
    NamespaceUtil.checkValueDomain(namespace);
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
          throw new IllegalArgumentException("ValueDomain namespace only supports 3 levels");
      }
    }
  }

  /** 根据遗留时间线删除值域元数据 */
  public int deleteValueDomainMetasByLegacyTimeline(Long legacyTimeline, int limit) {
    return SessionUtils.doWithCommitAndFetchResult(
        ValueDomainMetaMapper.class,
        mapper -> mapper.deleteValueDomainMetasByLegacyTimeline(legacyTimeline, limit));
  }
}
