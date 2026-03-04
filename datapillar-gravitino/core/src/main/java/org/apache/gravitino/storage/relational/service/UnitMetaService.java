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
import org.apache.gravitino.meta.UnitEntity;
import org.apache.gravitino.storage.relational.mapper.UnitMetaMapper;
import org.apache.gravitino.storage.relational.po.UnitPO;
import org.apache.gravitino.storage.relational.utils.ExceptionUtils;
import org.apache.gravitino.storage.relational.utils.POConverters;
import org.apache.gravitino.storage.relational.utils.SessionUtils;
import org.apache.gravitino.utils.NameIdentifierUtil;
import org.apache.gravitino.utils.NamespaceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Unit Metadata service class */
public class UnitMetaService {
  private static final Logger LOG = LoggerFactory.getLogger(UnitMetaService.class);
  private static final UnitMetaService INSTANCE = new UnitMetaService();

  public static UnitMetaService getInstance() {
    return INSTANCE;
  }

  private UnitMetaService() {}

  /** Insert Unit */
  public void insertUnit(UnitEntity unitEntity, boolean overwrite) throws IOException {
    try {
      NameIdentifierUtil.checkUnit(unitEntity.nameIdentifier());

      UnitPO.Builder builder = UnitPO.builder();
      fillUnitPOBuilderParentEntityId(builder, unitEntity.namespace());

      SessionUtils.doWithCommit(
          UnitMetaMapper.class,
          mapper -> {
            UnitPO po = POConverters.initializeUnitPO(unitEntity, builder);
            if (overwrite) {
              mapper.insertUnitMetaOnDuplicateKeyUpdate(po);
            } else {
              mapper.insertUnitMeta(po);
            }
          });
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.UNIT, unitEntity.nameIdentifier().toString());
      throw re;
    }
  }

  /** According to namespace list all Unit */
  public List<UnitEntity> listUnitsByNamespace(Namespace namespace) {
    NamespaceUtil.checkUnit(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);
    LOG.debug("listUnitsByNamespace - namespace={}, schemaId={}", namespace, schemaId);

    List<UnitPO> unitPOs =
        SessionUtils.getWithoutCommit(
            UnitMetaMapper.class, mapper -> mapper.listUnitPOsBySchemaId(schemaId));
    LOG.debug("listUnitsByNamespace - Found {} a UnitPO", unitPOs.size());

    List<UnitEntity> result = POConverters.fromUnitPOs(unitPOs, namespace);
    LOG.debug("listUnitsByNamespace - Return after conversion {} a Entity", result.size());
    return result;
  }

  /** List in pages Unit */
  public List<UnitEntity> listUnitsByNamespaceWithPagination(
      Namespace namespace, int offset, int limit) {
    NamespaceUtil.checkUnit(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    List<UnitPO> unitPOs =
        SessionUtils.getWithoutCommit(
            UnitMetaMapper.class,
            mapper -> mapper.listUnitPOsBySchemaIdWithPagination(schemaId, offset, limit));

    return POConverters.fromUnitPOs(unitPOs, namespace);
  }

  /** statistics Unit total */
  public long countUnitsByNamespace(Namespace namespace) {
    NamespaceUtil.checkUnit(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    return SessionUtils.getWithoutCommit(
        UnitMetaMapper.class, mapper -> mapper.countUnitsBySchemaId(schemaId));
  }

  /** Get Unit */
  public UnitEntity getUnitByIdentifier(NameIdentifier ident) {
    NameIdentifierUtil.checkUnit(ident);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    UnitPO unitPO = getUnitPOBySchemaIdAndCode(schemaId, ident.name());

    return POConverters.fromUnitPO(unitPO, ident.namespace());
  }

  /** update Unit */
  public <E extends Entity & HasIdentifier> UnitEntity updateUnit(
      NameIdentifier ident, Function<E, E> updater) throws IOException {
    NameIdentifierUtil.checkUnit(ident);

    String unitCode = ident.name();
    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());

    UnitPO oldUnitPO = getUnitPOBySchemaIdAndCode(schemaId, unitCode);
    UnitEntity oldUnitEntity = POConverters.fromUnitPO(oldUnitPO, ident.namespace());
    UnitEntity newEntity = (UnitEntity) updater.apply((E) oldUnitEntity);
    Preconditions.checkArgument(
        Objects.equals(oldUnitEntity.id(), newEntity.id()),
        "The updated unit entity id: %s should be same with the unit entity id before: %s",
        newEntity.id(),
        oldUnitEntity.id());

    Integer updateResult;
    try {
      updateResult =
          SessionUtils.doWithCommitAndFetchResult(
              UnitMetaMapper.class,
              mapper ->
                  mapper.updateUnitMeta(
                      POConverters.updateUnitPO(oldUnitPO, newEntity), oldUnitPO));
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.UNIT, newEntity.nameIdentifier().toString());
      throw re;
    }

    if (updateResult > 0) {
      return newEntity;
    } else {
      throw new IOException("Failed to update the entity: " + ident);
    }
  }

  /** Delete Unit */
  public boolean deleteUnit(NameIdentifier ident) {
    NameIdentifierUtil.checkUnit(ident);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    String unitCode = ident.name();

    Integer deleteResult =
        SessionUtils.doWithCommitAndFetchResult(
            UnitMetaMapper.class,
            mapper -> mapper.softDeleteUnitMetaBySchemaIdAndUnitCode(schemaId, unitCode));

    return deleteResult > 0;
  }

  private UnitPO getUnitPOBySchemaIdAndCode(Long schemaId, String unitCode) {
    UnitPO unitPO =
        SessionUtils.getWithoutCommit(
            UnitMetaMapper.class,
            mapper -> mapper.selectUnitMetaBySchemaIdAndUnitCode(schemaId, unitCode));

    if (unitPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.UNIT.name().toLowerCase(),
          unitCode);
    }
    return unitPO;
  }

  private void fillUnitPOBuilderParentEntityId(UnitPO.Builder builder, Namespace namespace) {
    NamespaceUtil.checkUnit(namespace);
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
          throw new IllegalArgumentException("Unit namespace only supports 3 levels");
      }
    }
  }

  /** Remove unit metadata based on legacy timeline */
  public int deleteUnitMetasByLegacyTimeline(Long legacyTimeline, int limit) {
    return SessionUtils.doWithCommitAndFetchResult(
        UnitMetaMapper.class,
        mapper -> mapper.deleteUnitMetasByLegacyTimeline(legacyTimeline, limit));
  }
}
