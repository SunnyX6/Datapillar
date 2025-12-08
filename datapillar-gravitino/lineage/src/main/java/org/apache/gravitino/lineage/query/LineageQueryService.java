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
package org.apache.gravitino.lineage.query;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.gravitino.lineage.model.ColumnLineage;
import org.apache.gravitino.lineage.model.LineageEdge;
import org.apache.gravitino.lineage.model.LineageGraph;
import org.apache.gravitino.lineage.model.LineageNode;

/** Service for querying lineage data from storage */
public class LineageQueryService {

  private final DataSource dataSource;

  public LineageQueryService(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * Query lineage graph starting from a node
   *
   * @param nodeId Format: "dataset:namespace:name" or "job:namespace:name"
   * @param depth Maximum depth to traverse
   * @param direction "upstream", "downstream", or "both"
   * @return LineageGraph
   */
  public LineageGraph queryLineage(String nodeId, int depth, String direction) throws SQLException {
    String[] parts = nodeId.split(":", 3);
    if (parts.length != 3) {
      throw new IllegalArgumentException("Invalid nodeId format. Expected: type:namespace:name");
    }

    String type = parts[0].toUpperCase();
    String namespace = parts[1];
    String name = parts[2];

    // Get starting node ID
    Long startNodeId = getNodeId(type, namespace, name);
    if (startNodeId == null) {
      return new LineageGraph(new ArrayList<>(), new ArrayList<>());
    }

    Set<String> visitedNodes = new HashSet<>();
    Map<String, LineageNode> nodes = new HashMap<>();
    List<LineageEdge> edges = new ArrayList<>();

    // BFS traversal
    if ("upstream".equals(direction) || "both".equals(direction)) {
      traverseUpstream(type, startNodeId, depth, visitedNodes, nodes, edges);
    }

    if ("downstream".equals(direction) || "both".equals(direction)) {
      traverseDownstream(type, startNodeId, depth, visitedNodes, nodes, edges);
    }

    // Add start node
    addNode(type, namespace, name, startNodeId, nodes);

    return new LineageGraph(new ArrayList<>(nodes.values()), edges);
  }

  /**
   * Query column lineage for a specific dataset
   *
   * @param namespace Dataset namespace
   * @param datasetName Dataset name
   * @param direction "upstream", "downstream", or "both"
   * @return List of column lineages
   */
  public List<ColumnLineage> queryColumnLineage(
      String namespace, String datasetName, String direction) throws SQLException {
    Long datasetId = getDatasetId(namespace, datasetName);
    if (datasetId == null) {
      return new ArrayList<>();
    }

    String sql;
    if ("upstream".equals(direction)) {
      sql =
          "SELECT lc.source_column, lc.target_column, lc.transformation, "
              + "sd.namespace AS source_namespace, sd.dataset_name AS source_dataset "
              + "FROM lineage_columns lc "
              + "JOIN lineage_datasets sd ON lc.source_dataset_id = sd.dataset_id "
              + "WHERE lc.target_dataset_id = ? "
              + "ORDER BY lc.target_column";
    } else if ("downstream".equals(direction)) {
      sql =
          "SELECT lc.source_column, lc.target_column, lc.transformation, "
              + "td.namespace AS target_namespace, td.dataset_name AS target_dataset "
              + "FROM lineage_columns lc "
              + "JOIN lineage_datasets td ON lc.target_dataset_id = td.dataset_id "
              + "WHERE lc.source_dataset_id = ? "
              + "ORDER BY lc.source_column";
    } else {
      sql =
          "SELECT lc.source_column, lc.target_column, lc.transformation, "
              + "sd.namespace AS source_namespace, sd.dataset_name AS source_dataset, "
              + "td.namespace AS target_namespace, td.dataset_name AS target_dataset "
              + "FROM lineage_columns lc "
              + "LEFT JOIN lineage_datasets sd ON lc.source_dataset_id = sd.dataset_id "
              + "LEFT JOIN lineage_datasets td ON lc.target_dataset_id = td.dataset_id "
              + "WHERE lc.source_dataset_id = ? OR lc.target_dataset_id = ? "
              + "ORDER BY lc.source_column, lc.target_column";
    }

    List<ColumnLineage> lineages = new ArrayList<>();
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      if ("both".equals(direction)) {
        ps.setLong(1, datasetId);
        ps.setLong(2, datasetId);
      } else {
        ps.setLong(1, datasetId);
      }

      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          ColumnLineage lineage = new ColumnLineage();
          lineage.setSourceColumn(rs.getString("source_column"));
          lineage.setTargetColumn(rs.getString("target_column"));
          lineage.setTransformation(rs.getString("transformation"));

          if ("upstream".equals(direction)) {
            lineage.setSourceDataset(
                rs.getString("source_namespace") + ":" + rs.getString("source_dataset"));
            lineage.setTargetDataset(namespace + ":" + datasetName);
            lineage.setDirection("upstream");
          } else if ("downstream".equals(direction)) {
            lineage.setSourceDataset(namespace + ":" + datasetName);
            lineage.setTargetDataset(
                rs.getString("target_namespace") + ":" + rs.getString("target_dataset"));
            lineage.setDirection("downstream");
          } else {
            lineage.setSourceDataset(
                rs.getString("source_namespace") + ":" + rs.getString("source_dataset"));
            lineage.setTargetDataset(
                rs.getString("target_namespace") + ":" + rs.getString("target_dataset"));
            lineage.setDirection(
                lineage.getSourceDataset().equals(namespace + ":" + datasetName)
                    ? "downstream"
                    : "upstream");
          }
          lineages.add(lineage);
        }
      }
    }
    return lineages;
  }

  private void traverseUpstream(
      String startType,
      Long startId,
      int depth,
      Set<String> visited,
      Map<String, LineageNode> nodes,
      List<LineageEdge> edges)
      throws SQLException {
    Queue<TraversalNode> queue = new LinkedList<>();
    queue.offer(new TraversalNode(startType, startId, 0));

    while (!queue.isEmpty()) {
      TraversalNode current = queue.poll();
      if (current.depth >= depth) continue;

      String nodeKey = current.type + ":" + current.id;
      if (visited.contains(nodeKey)) continue;
      visited.add(nodeKey);

      // Find upstream edges
      String sql =
          "SELECT e.source_type, e.source_id, e.edge_type, "
              + "CASE WHEN e.source_type = 'JOB' THEN j.namespace ELSE d.namespace END AS namespace, "
              + "CASE WHEN e.source_type = 'JOB' THEN j.job_name ELSE d.dataset_name END AS name "
              + "FROM lineage_edges e "
              + "LEFT JOIN lineage_jobs j ON e.source_type = 'JOB' AND e.source_id = j.job_id "
              + "LEFT JOIN lineage_datasets d ON e.source_type = 'DATASET' AND e.source_id = d.dataset_id "
              + "WHERE e.target_type = ? AND e.target_id = ?";

      try (Connection conn = dataSource.getConnection();
          PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, current.type);
        ps.setLong(2, current.id);

        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            String sourceType = rs.getString("source_type");
            Long sourceId = rs.getLong("source_id");
            String namespace = rs.getString("namespace");
            String name = rs.getString("name");

            addNode(sourceType, namespace, name, sourceId, nodes);
            edges.add(
                new LineageEdge(
                    sourceType + ":" + sourceId, current.type + ":" + current.id, "INPUT"));

            queue.offer(new TraversalNode(sourceType, sourceId, current.depth + 1));
          }
        }
      }
    }
  }

  private void traverseDownstream(
      String startType,
      Long startId,
      int depth,
      Set<String> visited,
      Map<String, LineageNode> nodes,
      List<LineageEdge> edges)
      throws SQLException {
    Queue<TraversalNode> queue = new LinkedList<>();
    queue.offer(new TraversalNode(startType, startId, 0));

    while (!queue.isEmpty()) {
      TraversalNode current = queue.poll();
      if (current.depth >= depth) continue;

      String nodeKey = current.type + ":" + current.id;
      if (visited.contains(nodeKey)) continue;
      visited.add(nodeKey);

      // Find downstream edges
      String sql =
          "SELECT e.target_type, e.target_id, e.edge_type, "
              + "CASE WHEN e.target_type = 'JOB' THEN j.namespace ELSE d.namespace END AS namespace, "
              + "CASE WHEN e.target_type = 'JOB' THEN j.job_name ELSE d.dataset_name END AS name "
              + "FROM lineage_edges e "
              + "LEFT JOIN lineage_jobs j ON e.target_type = 'JOB' AND e.target_id = j.job_id "
              + "LEFT JOIN lineage_datasets d ON e.target_type = 'DATASET' AND e.target_id = d.dataset_id "
              + "WHERE e.source_type = ? AND e.source_id = ?";

      try (Connection conn = dataSource.getConnection();
          PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, current.type);
        ps.setLong(2, current.id);

        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) {
            String targetType = rs.getString("target_type");
            Long targetId = rs.getLong("target_id");
            String namespace = rs.getString("namespace");
            String name = rs.getString("name");

            addNode(targetType, namespace, name, targetId, nodes);
            edges.add(
                new LineageEdge(
                    current.type + ":" + current.id, targetType + ":" + targetId, "OUTPUT"));

            queue.offer(new TraversalNode(targetType, targetId, current.depth + 1));
          }
        }
      }
    }
  }

  private void addNode(
      String type, String namespace, String name, Long id, Map<String, LineageNode> nodes) {
    String nodeId = type + ":" + id;
    if (!nodes.containsKey(nodeId)) {
      nodes.put(nodeId, new LineageNode(nodeId, type, namespace, name));
    }
  }

  private Long getNodeId(String type, String namespace, String name) throws SQLException {
    if ("JOB".equals(type)) {
      return getJobId(namespace, name);
    } else if ("DATASET".equals(type)) {
      return getDatasetId(namespace, name);
    }
    return null;
  }

  private Long getJobId(String namespace, String name) throws SQLException {
    String sql = "SELECT job_id FROM lineage_jobs WHERE namespace = ? AND job_name = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, namespace);
      ps.setString(2, name);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getLong("job_id");
        }
      }
    }
    return null;
  }

  private Long getDatasetId(String namespace, String name) throws SQLException {
    String sql = "SELECT dataset_id FROM lineage_datasets WHERE namespace = ? AND dataset_name = ?";
    try (Connection conn = dataSource.getConnection();
        PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, namespace);
      ps.setString(2, name);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getLong("dataset_id");
        }
      }
    }
    return null;
  }

  private static class TraversalNode {
    String type;
    Long id;
    int depth;

    TraversalNode(String type, Long id, int depth) {
      this.type = type;
      this.id = id;
      this.depth = depth;
    }
  }
}
