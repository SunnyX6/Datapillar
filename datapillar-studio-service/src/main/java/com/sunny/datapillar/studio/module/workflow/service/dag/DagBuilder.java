package com.sunny.datapillar.studio.module.workflow.service.dag;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;

/**
 * DAGBuildercomponents responsibleDAGBuilderCore logic implementation
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class DagBuilder {

  @Getter private final MutableGraph<Long> graph;

  private final Set<Long> nodes = new HashSet<>();

  public DagBuilder() {
    this.graph = GraphBuilder.directed().allowsSelfLoops(false).build();
  }

  /** Add node */
  public DagBuilder addNode(Long nodeId) {
    graph.addNode(nodeId);
    nodes.add(nodeId);
    return this;
  }

  /**
   * Add edge（Dependencies）
   *
   * @param from upstream node（parent）
   * @param to downstream node（child）
   */
  public DagBuilder addEdge(Long from, Long to) {
    if (!nodes.contains(from)) {
      throw new DagValidationException("Node not found: " + from);
    }
    if (!nodes.contains(to)) {
      throw new DagValidationException("Node not found: " + to);
    }
    graph.putEdge(from, to);
    return this;
  }

  /** Verify if it is valid DAG（No loop） */
  public boolean isValidDag() {
    return !Graphs.hasCycle(graph);
  }

  /** Verify DAG，Throws an exception if there is a loop */
  public void validate() {
    if (Graphs.hasCycle(graph)) {
      throw new DagValidationException("DAG contains cycle, invalid workflow");
    }
  }

  /** Get all root nodes（Nodes with no upstream dependencies） */
  public Set<Long> getRootNodes() {
    Set<Long> roots = new HashSet<>();
    for (Long node : graph.nodes()) {
      if (graph.predecessors(node).isEmpty()) {
        roots.add(node);
      }
    }
    return roots;
  }

  /** Get all leaf nodes（Nodes with no downstream dependencies） */
  public Set<Long> getLeafNodes() {
    Set<Long> leaves = new HashSet<>();
    for (Long node : graph.nodes()) {
      if (graph.successors(node).isEmpty()) {
        leaves.add(node);
      }
    }
    return leaves;
  }

  /** Get all upstream nodes of a node */
  public Set<Long> getPredecessors(Long nodeId) {
    return graph.predecessors(nodeId);
  }

  /** Get all downstream nodes of a node */
  public Set<Long> getSuccessors(Long nodeId) {
    return graph.successors(nodeId);
  }

  /** topological sort - Return execution order */
  public List<Long> topologicalSort() {
    validate();

    List<Long> result = new ArrayList<>();
    Set<Long> visited = new HashSet<>();
    Set<Long> visiting = new HashSet<>();

    for (Long node : graph.nodes()) {
      if (!visited.contains(node)) {
        topologicalSortDfs(node, visited, visiting, result);
      }
    }

    // Reverse to get the correct order of execution
    java.util.Collections.reverse(result);
    return result;
  }

  private void topologicalSortDfs(
      Long node, Set<Long> visited, Set<Long> visiting, List<Long> result) {
    visiting.add(node);

    for (Long successor : graph.successors(node)) {
      if (visiting.contains(successor)) {
        throw new DagValidationException("Cycle detected at node: " + successor);
      }
      if (!visited.contains(successor)) {
        topologicalSortDfs(successor, visited, visiting, result);
      }
    }

    visiting.remove(node);
    visited.add(node);
    result.add(node);
  }

  /** Get the number of nodes */
  public int nodeCount() {
    return graph.nodes().size();
  }

  /** Get the number of edges */
  public int edgeCount() {
    return graph.edges().size();
  }
}
