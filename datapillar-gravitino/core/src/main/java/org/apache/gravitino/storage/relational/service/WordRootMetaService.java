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
import org.apache.gravitino.meta.WordRootEntity;
import org.apache.gravitino.storage.relational.mapper.WordRootMetaMapper;
import org.apache.gravitino.storage.relational.po.WordRootPO;
import org.apache.gravitino.storage.relational.utils.ExceptionUtils;
import org.apache.gravitino.storage.relational.utils.POConverters;
import org.apache.gravitino.storage.relational.utils.SessionUtils;
import org.apache.gravitino.utils.NameIdentifierUtil;
import org.apache.gravitino.utils.NamespaceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** WordRoot 元数据服务类 */
public class WordRootMetaService {
  private static final Logger LOG = LoggerFactory.getLogger(WordRootMetaService.class);
  private static final WordRootMetaService INSTANCE = new WordRootMetaService();

  public static WordRootMetaService getInstance() {
    return INSTANCE;
  }

  private WordRootMetaService() {}

  /** 插入 WordRoot */
  public void insertWordRoot(WordRootEntity wordRootEntity, boolean overwrite) throws IOException {
    try {
      NameIdentifierUtil.checkRoot(wordRootEntity.nameIdentifier());

      WordRootPO.Builder builder = WordRootPO.builder();
      fillWordRootPOBuilderParentEntityId(builder, wordRootEntity.namespace());

      SessionUtils.doWithCommit(
          WordRootMetaMapper.class,
          mapper -> {
            WordRootPO po = POConverters.initializeWordRootPO(wordRootEntity, builder);
            if (overwrite) {
              mapper.insertWordRootMetaOnDuplicateKeyUpdate(po);
            } else {
              mapper.insertWordRootMeta(po);
            }
          });
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.WORDROOT, wordRootEntity.nameIdentifier().toString());
      throw re;
    }
  }

  /** 根据 namespace 列出所有 WordRoot */
  public List<WordRootEntity> listWordRootsByNamespace(Namespace namespace) {
    NamespaceUtil.checkRoot(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);
    LOG.info("DEBUG: listWordRootsByNamespace - namespace={}, schemaId={}", namespace, schemaId);

    List<WordRootPO> wordRootPOs =
        SessionUtils.getWithoutCommit(
            WordRootMetaMapper.class, mapper -> mapper.listWordRootPOsBySchemaId(schemaId));
    LOG.info("DEBUG: listWordRootsByNamespace - 查询到 {} 个 WordRootPO", wordRootPOs.size());

    List<WordRootEntity> result = POConverters.fromWordRootPOs(wordRootPOs, namespace);
    LOG.info("DEBUG: listWordRootsByNamespace - 转换后返回 {} 个 Entity", result.size());
    return result;
  }

  /** 分页列出 WordRoot */
  public List<WordRootEntity> listWordRootsByNamespaceWithPagination(
      Namespace namespace, int offset, int limit) {
    NamespaceUtil.checkRoot(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    List<WordRootPO> wordRootPOs =
        SessionUtils.getWithoutCommit(
            WordRootMetaMapper.class,
            mapper -> mapper.listWordRootPOsBySchemaIdWithPagination(schemaId, offset, limit));

    return POConverters.fromWordRootPOs(wordRootPOs, namespace);
  }

  /** 统计 WordRoot 总数 */
  public long countWordRootsByNamespace(Namespace namespace) {
    NamespaceUtil.checkRoot(namespace);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(namespace);

    return SessionUtils.getWithoutCommit(
        WordRootMetaMapper.class, mapper -> mapper.countWordRootsBySchemaId(schemaId));
  }

  /** 获取 WordRoot */
  public WordRootEntity getWordRootByIdentifier(NameIdentifier ident) {
    NameIdentifierUtil.checkRoot(ident);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    WordRootPO wordRootPO = getWordRootPOBySchemaIdAndCode(schemaId, ident.name());

    return POConverters.fromWordRootPO(wordRootPO, ident.namespace());
  }

  /** 更新 WordRoot */
  public <E extends Entity & HasIdentifier> WordRootEntity updateWordRoot(
      NameIdentifier ident, Function<E, E> updater) throws IOException {
    NameIdentifierUtil.checkRoot(ident);

    String rootCode = ident.name();
    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());

    WordRootPO oldWordRootPO = getWordRootPOBySchemaIdAndCode(schemaId, rootCode);
    WordRootEntity oldWordRootEntity =
        POConverters.fromWordRootPO(oldWordRootPO, ident.namespace());
    WordRootEntity newEntity = (WordRootEntity) updater.apply((E) oldWordRootEntity);
    Preconditions.checkArgument(
        Objects.equals(oldWordRootEntity.id(), newEntity.id()),
        "The updated word root entity id: %s should be same with the word root entity id before: %s",
        newEntity.id(),
        oldWordRootEntity.id());

    Integer updateResult;
    try {
      updateResult =
          SessionUtils.doWithCommitAndFetchResult(
              WordRootMetaMapper.class,
              mapper ->
                  mapper.updateWordRootMeta(
                      POConverters.updateWordRootPO(oldWordRootPO, newEntity), oldWordRootPO));
    } catch (RuntimeException re) {
      ExceptionUtils.checkSQLException(
          re, Entity.EntityType.WORDROOT, newEntity.nameIdentifier().toString());
      throw re;
    }

    if (updateResult > 0) {
      return newEntity;
    } else {
      throw new IOException("Failed to update the entity: " + ident);
    }
  }

  /** 删除 WordRoot */
  public boolean deleteWordRoot(NameIdentifier ident) {
    NameIdentifierUtil.checkRoot(ident);

    Long schemaId = CommonMetaService.getInstance().getParentEntityIdByNamespace(ident.namespace());
    String rootCode = ident.name();

    Integer deleteResult =
        SessionUtils.doWithCommitAndFetchResult(
            WordRootMetaMapper.class,
            mapper -> mapper.softDeleteWordRootMetaBySchemaIdAndRootCode(schemaId, rootCode));

    return deleteResult > 0;
  }

  private WordRootPO getWordRootPOBySchemaIdAndCode(Long schemaId, String rootCode) {
    WordRootPO wordRootPO =
        SessionUtils.getWithoutCommit(
            WordRootMetaMapper.class,
            mapper -> mapper.selectWordRootMetaBySchemaIdAndRootCode(schemaId, rootCode));

    if (wordRootPO == null) {
      throw new NoSuchEntityException(
          NoSuchEntityException.NO_SUCH_ENTITY_MESSAGE,
          Entity.EntityType.WORDROOT.name().toLowerCase(),
          rootCode);
    }
    return wordRootPO;
  }

  private void fillWordRootPOBuilderParentEntityId(
      WordRootPO.Builder builder, Namespace namespace) {
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
          throw new IllegalArgumentException("WordRoot namespace only supports 3 levels");
      }
    }
  }

  /** 根据遗留时间线删除词根元数据 */
  public int deleteWordRootMetasByLegacyTimeline(Long legacyTimeline, int limit) {
    return SessionUtils.doWithCommitAndFetchResult(
        WordRootMetaMapper.class,
        mapper -> mapper.deleteWordRootMetasByLegacyTimeline(legacyTimeline, limit));
  }
}
