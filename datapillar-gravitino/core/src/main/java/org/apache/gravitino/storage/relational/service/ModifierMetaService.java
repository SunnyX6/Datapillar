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
import org.apache.gravitino.meta.ModifierEntity;
import org.apache.gravitino.storage.relational.mapper.ModifierMetaMapper;
import org.apache.gravitino.storage.relational.po.ModifierPO;
import org.apache.gravitino.storage.relational.utils.ExceptionUtils;
import org.apache.gravitino.storage.relational.utils.POConverters;
import org.apache.gravitino.storage.relational.utils.SessionUtils;
import org.apache.gravitino.utils.NameIdentifierUtil;
import org.apache.gravitino.utils.NamespaceUtil;

/** Modifier Metadata service class */
public class ModifierMetaService {
  private static final ModifierMetaService INSTANCE = new ModifierMetaService();

  public static ModifierMetaService getInstance() {
    return INSTANCE;
  }

  private ModifierMetaService() {}

  /** Insert Modifier */
  public void insertModifier(ModifierEntity modifierEntity, boolean overwrite) throws IOException {
    try {
      NameIdentifierUtil.checkModifier(modifierEntity.nameIdentifier());

      ModifierPO.Builder builder = ModifierPO.builder();
      fillModifierPOBuilderParentEntityId(builder, modifierEntity.namespace());

      SessionUtils.doWithCommit(
          ModifierMetaMapper.class,
          mapper -> {
            ModifierPO po = POConverters.initializeModifierPO(modifierEntity, builder);
            if (overwrite) {
              mapper.insertModifierMetaOnDuplicateKeyUpdate(po);
            } else {
              mapper.insertModifierMeta(po);
            }
          });
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.MODIFIER, modifierEntity.nameIdentifier().toString());
      throw re;
    }
  }

  /** According to namespace list all Modifier */
  public List<ModifierEntity> listModifiersByNamespace(Namespace namespace) {
    NamespaceUtil.checkModifier(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    List<ModifierPO> modifierPOs =
        SessionUtils.getWithoutCommit(
            ModifierMetaMapper.class, mapper -> mapper.listModifierPOsBySchemaId(schemaId));

    return POConverters.fromModifierPOs(modifierPOs, namespace);
  }

  /** List in pages Modifier */
  public List<ModifierEntity> listModifiersByNamespaceWithPagination(
      Namespace namespace, int offset, int limit) {
    NamespaceUtil.checkModifier(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    List<ModifierPO> modifierPOs =
        SessionUtils.getWithoutCommit(
            ModifierMetaMapper.class,
            mapper -> mapper.listModifierPOsBySchemaIdWithPagination(schemaId, offset, limit));

    return POConverters.fromModifierPOs(modifierPOs, namespace);
  }

  /** statistics Modifier total */
  public long countModifiersByNamespace(Namespace namespace) {
    NamespaceUtil.checkModifier(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    return SessionUtils.getWithoutCommit(
        ModifierMetaMapper.class, mapper -> mapper.countModifiersBySchemaId(schemaId));
  }

  /** Get Modifier */
  public ModifierEntity getModifierByIdentifier(NameIdentifier ident) {
    NameIdentifierUtil.checkModifier(ident);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    ModifierPO modifierPO = getModifierPOBySchemaIdAndCode(schemaId, ident.name());

    return POConverters.fromModifierPO(modifierPO, ident.namespace());
  }

  /** update Modifier */
  public <E extends Entity & HasIdentifier> ModifierEntity updateModifier(
      NameIdentifier ident, Function<E, E> updater) throws IOException {
    NameIdentifierUtil.checkModifier(ident);

    String modifierCode = ident.name();
    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());

    ModifierPO oldModifierPO = getModifierPOBySchemaIdAndCode(schemaId, modifierCode);
    ModifierEntity oldModifierEntity =
        POConverters.fromModifierPO(oldModifierPO, ident.namespace());
    ModifierEntity newEntity = (ModifierEntity) updater.apply((E) oldModifierEntity);
    Preconditions.checkArgument(
        Objects.equals(oldModifierEntity.id(), newEntity.id()),
        "The updated modifier entity id: %s should be same with the modifier entity id before: %s",
        newEntity.id(),
        oldModifierEntity.id());

    Integer updateResult;
    try {
      updateResult =
          SessionUtils.doWithCommitAndFetchResult(
              ModifierMetaMapper.class,
              mapper ->
                  mapper.updateModifierMeta(
                      POConverters.updateModifierPO(oldModifierPO, newEntity), oldModifierPO));
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.MODIFIER, newEntity.nameIdentifier().toString());
      throw re;
    }

    if (updateResult > 0) {
      return newEntity;
    } else {
      throw new IOException("Failed to update the entity: " + ident);
    }
  }

  /** Delete Modifier */
  public boolean deleteModifier(NameIdentifier ident) {
    NameIdentifierUtil.checkModifier(ident);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    String modifierCode = ident.name();

    Integer deleteResult =
        SessionUtils.doWithCommitAndFetchResult(
            ModifierMetaMapper.class,
            mapper ->
                mapper.softDeleteModifierMetaBySchemaIdAndModifierCode(schemaId, modifierCode));

    return deleteResult > 0;
  }

  /** According to schemaId and code Get modifierId */
  public Long getModifierIdBySchemaIdAndCode(Long schemaId, String modifierCode) {
    Long modifierId =
        SessionUtils.getWithoutCommit(
            ModifierMetaMapper.class,
            mapper -> mapper.selectModifierIdBySchemaIdAndModifierCode(schemaId, modifierCode));

    if (modifierId == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.MODIFIER.name().toLowerCase(),
          modifierCode);
    }
    return modifierId;
  }

  private ModifierPO getModifierPOBySchemaIdAndCode(Long schemaId, String modifierCode) {
    ModifierPO modifierPO =
        SessionUtils.getWithoutCommit(
            ModifierMetaMapper.class,
            mapper -> mapper.selectModifierMetaBySchemaIdAndModifierCode(schemaId, modifierCode));

    if (modifierPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.MODIFIER.name().toLowerCase(),
          modifierCode);
    }
    return modifierPO;
  }

  private void fillModifierPOBuilderParentEntityId(
      ModifierPO.Builder builder, Namespace namespace) {
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

  /** Remove modifier metadata based on legacy timeline */
  public int deleteModifierMetasByLegacyTimeline(Long legacyTimeline, int limit) {
    return SessionUtils.doWithCommitAndFetchResult(
        ModifierMetaMapper.class,
        mapper -> mapper.deleteModifierMetasByLegacyTimeline(legacyTimeline, limit));
  }
}
