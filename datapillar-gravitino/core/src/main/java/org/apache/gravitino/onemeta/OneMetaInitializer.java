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
package org.apache.gravitino.onemeta;

import java.util.Collections;
import org.apache.gravitino.Catalog;
import org.apache.gravitino.Config;
import org.apache.gravitino.Configs;
import org.apache.gravitino.NameIdentifier;
import org.apache.gravitino.catalog.CatalogDispatcher;
import org.apache.gravitino.catalog.SchemaDispatcher;
import org.apache.gravitino.exceptions.CatalogAlreadyExistsException;
import org.apache.gravitino.exceptions.MetalakeAlreadyExistsException;
import org.apache.gravitino.exceptions.SchemaAlreadyExistsException;
import org.apache.gravitino.metalake.MetalakeDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OneMeta 初始化器，在 Gravitino 启动时自动检查并创建：
 *
 * <ul>
 *   <li>Metalake（默认 OneMeta）
 *   <li>Dataset Catalog（默认 OneMeta）
 *   <li>Schema（默认 OneMeta）
 * </ul>
 *
 * <p>注意：值域（ValueDomain）由用户自行创建和管理，不再系统内置。
 */
public class OneMetaInitializer {

  private static final Logger LOG = LoggerFactory.getLogger(OneMetaInitializer.class);

  /** Dataset Catalog Provider 名称 */
  private static final String DATASET_PROVIDER = "dataset";

  private final Config config;
  private final MetalakeDispatcher metalakeDispatcher;
  private final CatalogDispatcher catalogDispatcher;
  private final SchemaDispatcher schemaDispatcher;

  public OneMetaInitializer(
      Config config,
      MetalakeDispatcher metalakeDispatcher,
      CatalogDispatcher catalogDispatcher,
      SchemaDispatcher schemaDispatcher) {
    this.config = config;
    this.metalakeDispatcher = metalakeDispatcher;
    this.catalogDispatcher = catalogDispatcher;
    this.schemaDispatcher = schemaDispatcher;
  }

  /** 执行初始化 */
  public void initialize() {
    boolean enabled = config.get(Configs.ONEMETA_ENABLED);
    if (!enabled) {
      LOG.info("OneMeta auto initialization is disabled");
      return;
    }

    String metalakeName = config.get(Configs.ONEMETA_METALAKE);
    String catalogName = config.get(Configs.ONEMETA_CATALOG_DATASET);
    String schemaName = config.get(Configs.ONEMETA_SCHEMA_DATASET);

    LOG.info(
        "Initializing OneMeta: metalake={}, catalog={}, schema={}",
        metalakeName,
        catalogName,
        schemaName);

    try {
      // 1. 检查并创建 Metalake
      ensureMetalakeExists(metalakeName);

      // 2. 检查并创建 Dataset Catalog
      ensureCatalogExists(metalakeName, catalogName);

      // 3. 检查并创建 Schema
      ensureSchemaExists(metalakeName, catalogName, schemaName);

      LOG.info("OneMeta initialization completed successfully");
    } catch (Exception e) {
      LOG.error("Failed to initialize OneMeta", e);
    }
  }

  private void ensureMetalakeExists(String metalakeName) {
    NameIdentifier metalakeIdent = NameIdentifier.of(metalakeName);

    try {
      if (metalakeDispatcher.metalakeExists(metalakeIdent)) {
        LOG.info("Metalake '{}' already exists", metalakeName);
        return;
      }
    } catch (Exception e) {
      LOG.debug("Error checking metalake existence, will try to create: {}", e.getMessage());
    }

    try {
      metalakeDispatcher.createMetalake(
          metalakeIdent, "OneMeta - 元数据语义资产管理", Collections.emptyMap());
      LOG.info("Created metalake '{}'", metalakeName);
    } catch (MetalakeAlreadyExistsException e) {
      LOG.info("Metalake '{}' already exists (concurrent creation)", metalakeName);
    }
  }

  private void ensureCatalogExists(String metalakeName, String catalogName) {
    NameIdentifier catalogIdent = NameIdentifier.of(metalakeName, catalogName);

    try {
      if (catalogDispatcher.catalogExists(catalogIdent)) {
        LOG.info("Catalog '{}.{}' already exists", metalakeName, catalogName);
        return;
      }
    } catch (Exception e) {
      LOG.debug("Error checking catalog existence, will try to create: {}", e.getMessage());
    }

    try {
      catalogDispatcher.createCatalog(
          catalogIdent,
          Catalog.Type.DATASET,
          DATASET_PROVIDER,
          "OneMeta Dataset Catalog",
          Collections.emptyMap());
      LOG.info("Created catalog '{}.{}'", metalakeName, catalogName);
    } catch (CatalogAlreadyExistsException e) {
      LOG.info("Catalog '{}.{}' already exists (concurrent creation)", metalakeName, catalogName);
    }
  }

  private void ensureSchemaExists(String metalakeName, String catalogName, String schemaName) {
    NameIdentifier schemaIdent = NameIdentifier.of(metalakeName, catalogName, schemaName);

    try {
      if (schemaDispatcher.schemaExists(schemaIdent)) {
        LOG.info("Schema '{}.{}.{}' already exists", metalakeName, catalogName, schemaName);
        return;
      }
    } catch (Exception e) {
      LOG.debug("Error checking schema existence, will try to create: {}", e.getMessage());
    }

    try {
      schemaDispatcher.createSchema(schemaIdent, "OneMeta Default Schema", Collections.emptyMap());
      LOG.info("Created schema '{}.{}.{}'", metalakeName, catalogName, schemaName);
    } catch (SchemaAlreadyExistsException e) {
      LOG.info(
          "Schema '{}.{}.{}' already exists (concurrent creation)",
          metalakeName,
          catalogName,
          schemaName);
    }
  }
}
