/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.gravitino.lineage;

import com.google.common.collect.ImmutableSet;
import io.openlineage.server.OpenLineage;
import io.openlineage.server.OpenLineage.RunEvent;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.gravitino.lineage.model.ColumnLineage;
import org.apache.gravitino.lineage.model.LineageGraph;
import org.apache.gravitino.lineage.processor.LineageProcessor;
import org.apache.gravitino.lineage.query.LineageQueryService;
import org.apache.gravitino.lineage.sink.LineageSinkManager;
import org.apache.gravitino.lineage.source.LineageSource;
import org.apache.gravitino.server.web.SupportsRESTPackages;
import org.apache.gravitino.utils.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The LineageService manages the life cycle of lineage sinks, sources, and processors. It provides
 * {@code dispatchLineageEvent} method for lineage source to dispatch lineage events to the sinks.
 */
public class LineageService implements LineageDispatcher, SupportsRESTPackages {
  private static final Logger LOG = LoggerFactory.getLogger(LineageService.class);
  private LineageSinkManager sinkManager;
  private LineageSource source;
  private LineageProcessor processor;
  private LineageQueryService queryService;
  private DataSource queryDataSource;

  public void initialize(LineageConfig lineageConfig) {
    String sourceName = lineageConfig.source();
    String sourceClass = lineageConfig.sourceClass();
    this.source = ClassUtils.loadClass(sourceClass);
    this.sinkManager = new LineageSinkManager();

    String processorClassName = lineageConfig.processorClass();
    this.processor = ClassUtils.loadClass(processorClassName);

    sinkManager.initialize(lineageConfig.sinks(), lineageConfig.getSinkConfigs());
    source.initialize(lineageConfig.getConfigsWithPrefix(sourceName), this);

    // Initialize query service if storage sink is configured
    if (lineageConfig.sinks().contains("storage")) {
      initializeQueryService(lineageConfig);
    }
  }

  private void initializeQueryService(LineageConfig lineageConfig) {
    try {
      String jdbcUrl = lineageConfig.getRawString("storage.jdbcUrl");
      String jdbcDriver = lineageConfig.getRawString("storage.jdbcDriver");
      String jdbcUser = lineageConfig.getRawString("storage.jdbcUser");
      String jdbcPassword = lineageConfig.getRawString("storage.jdbcPassword");

      LOG.info("Initializing LineageQueryService with JDBC URL: {}", jdbcUrl);

      BasicDataSource basicDataSource = new BasicDataSource();
      basicDataSource.setUrl(jdbcUrl);
      basicDataSource.setDriverClassName(jdbcDriver);
      basicDataSource.setUsername(jdbcUser);
      basicDataSource.setPassword(jdbcPassword);
      basicDataSource.setMaxTotal(10);
      basicDataSource.setMinIdle(2);
      basicDataSource.setTestOnBorrow(true);
      basicDataSource.setValidationQuery("SELECT 1");

      this.queryDataSource = basicDataSource;
      this.queryService = new LineageQueryService(queryDataSource);

      LOG.info("LineageQueryService initialized successfully");
    } catch (Exception e) {
      LOG.warn("Failed to initialize LineageQueryService, query features will be disabled", e);
    }
  }

  @Override
  public void close() {
    if (source != null) {
      source.close();
      source = null;
    }
    if (sinkManager != null) {
      sinkManager.close();
      sinkManager = null;
    }
    if (queryDataSource != null) {
      try {
        if (queryDataSource instanceof BasicDataSource) {
          ((BasicDataSource) queryDataSource).close();
        }
      } catch (Exception e) {
        LOG.warn("Error closing query data source", e);
      }
      queryDataSource = null;
    }
  }

  @Override
  public boolean dispatchLineageEvent(OpenLineage.RunEvent runEvent) {
    if (sinkManager.isHighWatermark()) {
      return false;
    }

    RunEvent newEvent = processor.process(runEvent);
    sinkManager.sink(newEvent);
    return true;
  }

  @Override
  public Set<String> getRESTPackages() {
    if (source instanceof SupportsRESTPackages) {
      return ((SupportsRESTPackages) source).getRESTPackages();
    }
    return ImmutableSet.of();
  }

  @Override
  public LineageGraph queryLineage(String nodeId, int depth, String direction) throws SQLException {
    if (queryService == null) {
      throw new UnsupportedOperationException(
          "Lineage query is not enabled. Please configure storage sink.");
    }
    return queryService.queryLineage(nodeId, depth, direction);
  }

  @Override
  public List<ColumnLineage> queryColumnLineage(
      String namespace, String datasetName, String direction) throws SQLException {
    if (queryService == null) {
      throw new UnsupportedOperationException(
          "Lineage query is not enabled. Please configure storage sink.");
    }
    return queryService.queryColumnLineage(namespace, datasetName, direction);
  }
}
