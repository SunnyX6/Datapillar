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
package org.apache.gravitino.lineage.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openlineage.server.OpenLineage.Dataset;
import io.openlineage.server.OpenLineage.InputDataset;
import io.openlineage.server.OpenLineage.Job;
import io.openlineage.server.OpenLineage.OutputDataset;
import io.openlineage.server.OpenLineage.RunEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.gravitino.lineage.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LineageStorageSink stores OpenLineage events in Gravitino's database. This allows querying
 * lineage data without external dependencies like Marquez.
 */
public class LineageStorageSink implements LineageSink {

  private static final Logger LOG = LoggerFactory.getLogger(LineageStorageSink.class);
  private DataSource dataSource;
  private ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void initialize(Map<String, String> configs) {
    String jdbcUrl = configs.get("jdbcUrl");
    String jdbcDriver = configs.get("jdbcDriver");
    String jdbcUser = configs.get("jdbcUser");
    String jdbcPassword = configs.get("jdbcPassword");

    LOG.info("Initializing LineageStorageSink with JDBC URL: {}", jdbcUrl);

    BasicDataSource basicDataSource = new BasicDataSource();
    basicDataSource.setUrl(jdbcUrl);
    basicDataSource.setDriverClassName(jdbcDriver);
    basicDataSource.setUsername(jdbcUser);
    basicDataSource.setPassword(jdbcPassword);
    basicDataSource.setMaxTotal(10);
    basicDataSource.setMinIdle(2);
    basicDataSource.setMaxWait(Duration.ofMillis(30000));
    basicDataSource.setTestOnBorrow(true);
    basicDataSource.setValidationQuery("SELECT 1");

    this.dataSource = basicDataSource;
    LOG.info("LineageStorageSink initialized successfully");
  }

  @Override
  public void sink(RunEvent event) {
    try {
      long now = System.currentTimeMillis();

      // 1. Save Job
      Long jobId = saveJob(event.getJob(), now);

      // 2. Process Input Datasets
      if (event.getInputs() != null) {
        for (InputDataset input : event.getInputs()) {
          Long datasetId = saveDataset(input, now);
          createEdge(datasetId, "DATASET", jobId, "JOB", "INPUT", Utils.getRunID(event), now);
        }
      }

      // 3. Process Output Datasets
      if (event.getOutputs() != null) {
        for (OutputDataset output : event.getOutputs()) {
          Long datasetId = saveDataset(output, now);
          createEdge(jobId, "JOB", datasetId, "DATASET", "OUTPUT", Utils.getRunID(event), now);

          // 4. Save Column Lineage if available
          saveColumnLineage(output, datasetId, now);
        }
      }

      LOG.info(
          "Saved lineage event: job={}, run={}", event.getJob().getName(), Utils.getRunID(event));

    } catch (Exception e) {
      LOG.error("Failed to save lineage event", e);
    }
  }

  private Long saveJob(Job job, long timestamp) throws SQLException {
    String namespace = job.getNamespace();
    String name = job.getName();

    // Extract job facets
    String jobFacets = null;
    try {
      if (job.getFacets() != null && !job.getFacets().getAdditionalProperties().isEmpty()) {
        jobFacets = objectMapper.writeValueAsString(job.getFacets());

        // Log SQL facet if present
        if (job.getFacets().getAdditionalProperties() != null) {
          Map<String, Object> facets = new HashMap<>(job.getFacets().getAdditionalProperties());
          if (facets.containsKey("sql")) {
            Object sqlFacet = facets.get("sql");
            LOG.info("Found SQL facet in job {}: {}", name, sqlFacet);
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Failed to serialize job facets for job: " + namespace + ":" + name, e);
    }

    // Try to get existing job first
    String selectSql = "SELECT job_id FROM lineage_jobs WHERE namespace = ? AND job_name = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(selectSql)) {
      ps.setString(1, namespace);
      ps.setString(2, name);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          Long jobId = rs.getLong("job_id");
          // Update timestamp and job facets
          String updateSql =
              "UPDATE lineage_jobs SET updated_at = ?, job_facets = ? WHERE job_id = ?";
          try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
            updatePs.setLong(1, timestamp);
            updatePs.setString(2, jobFacets);
            updatePs.setLong(3, jobId);
            updatePs.executeUpdate();
          }
          return jobId;
        }
      }
    }

    // Insert new job if not exists
    String insertSql =
        "INSERT INTO lineage_jobs (namespace, job_name, job_facets, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(insertSql)) {
      ps.setString(1, namespace);
      ps.setString(2, name);
      ps.setString(3, jobFacets);
      ps.setLong(4, timestamp);
      ps.setLong(5, timestamp);
      ps.executeUpdate();
    }

    // Get the inserted job_id
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(selectSql)) {
      ps.setString(1, namespace);
      ps.setString(2, name);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getLong("job_id");
        }
      }
    }
    throw new SQLException("Failed to get job_id for job: " + namespace + ":" + name);
  }

  private Long saveDataset(Dataset dataset, long timestamp) throws SQLException {
    String schemaJson = null;
    try {
      if (dataset.getFacets() != null
          && dataset.getFacets().getAdditionalProperties() != null
          && dataset.getFacets().getAdditionalProperties().containsKey("schema")) {
        schemaJson =
            objectMapper.writeValueAsString(
                dataset.getFacets().getAdditionalProperties().get("schema"));
      }
    } catch (Exception e) {
      LOG.warn("Failed to serialize dataset schema", e);
    }

    // Try to get existing dataset first
    String selectSql =
        "SELECT dataset_id FROM lineage_datasets WHERE namespace = ? AND dataset_name = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(selectSql)) {
      ps.setString(1, dataset.getNamespace());
      ps.setString(2, dataset.getName());
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          Long datasetId = rs.getLong("dataset_id");
          // Update timestamp and schema
          String updateSql =
              "UPDATE lineage_datasets SET updated_at = ?, schema_json = ? WHERE dataset_id = ?";
          try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
            updatePs.setLong(1, timestamp);
            updatePs.setString(2, schemaJson);
            updatePs.setLong(3, datasetId);
            updatePs.executeUpdate();
          }
          return datasetId;
        }
      }
    }

    // Insert new dataset if not exists
    String insertSql =
        "INSERT INTO lineage_datasets (namespace, dataset_name, schema_json, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?)";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(insertSql)) {
      ps.setString(1, dataset.getNamespace());
      ps.setString(2, dataset.getName());
      ps.setString(3, schemaJson);
      ps.setLong(4, timestamp);
      ps.setLong(5, timestamp);
      ps.executeUpdate();
    }

    // Get the inserted dataset_id
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(selectSql)) {
      ps.setString(1, dataset.getNamespace());
      ps.setString(2, dataset.getName());
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getLong("dataset_id");
        }
      }
    }
    throw new SQLException(
        "Failed to get dataset_id for dataset: "
            + dataset.getNamespace()
            + ":"
            + dataset.getName());
  }

  private void createEdge(
      Long sourceId,
      String sourceType,
      Long targetId,
      String targetType,
      String edgeType,
      String runId,
      long timestamp)
      throws SQLException {
    String sql =
        "INSERT INTO lineage_edges (source_id, source_type, target_id, target_type, "
            + "edge_type, run_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, sourceId);
      ps.setString(2, sourceType);
      ps.setLong(3, targetId);
      ps.setString(4, targetType);
      ps.setString(5, edgeType);
      ps.setString(6, runId);
      ps.setLong(7, timestamp);
      ps.executeUpdate();
    }
  }

  @SuppressWarnings("unused")
  private void saveColumnLineage(OutputDataset output, Long datasetId, long timestamp) {
    // TODO: Extract column lineage from outputDataset facets
    // This requires parsing columnLineageDatasetFacet from OpenLineage spec
    // For now, we skip this - can be implemented later based on actual usage
    LOG.debug("Column lineage extraction not yet implemented for dataset: {}", output.getName());
  }

  @Override
  public void close() {
    if (dataSource != null) {
      try {
        if (dataSource instanceof BasicDataSource) {
          ((BasicDataSource) dataSource).close();
        }
        LOG.info("LineageStorageSink closed");
      } catch (Exception e) {
        LOG.warn("Error closing LineageStorageSink data source", e);
      }
    }
  }
}
