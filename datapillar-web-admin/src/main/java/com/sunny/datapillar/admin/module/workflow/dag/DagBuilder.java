package com.sunny.datapillar.admin.module.workflow.dag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

import lombok.Getter;

/**
 * DAG 构建器 - 使用 Guava Graph 构建和验证有向无环图
 *
 * @author sunny
 */
public class DagBuilder {

    @Getter
    private final MutableGraph<Long> graph;

    private final Set<Long> nodes = new HashSet<>();

    public DagBuilder() {
        this.graph = GraphBuilder.directed()
                .allowsSelfLoops(false)
                .build();
    }

    /**
     * 添加节点
     */
    public DagBuilder addNode(Long nodeId) {
        graph.addNode(nodeId);
        nodes.add(nodeId);
        return this;
    }

    /**
     * 添加边（依赖关系）
     *
     * @param from 上游节点（parent）
     * @param to   下游节点（child）
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

    /**
     * 验证是否为有效 DAG（无环）
     */
    public boolean isValidDag() {
        return !Graphs.hasCycle(graph);
    }

    /**
     * 验证 DAG，如果有环则抛出异常
     */
    public void validate() {
        if (Graphs.hasCycle(graph)) {
            throw new DagValidationException("DAG contains cycle, invalid workflow");
        }
    }

    /**
     * 获取所有根节点（没有上游依赖的节点）
     */
    public Set<Long> getRootNodes() {
        Set<Long> roots = new HashSet<>();
        for (Long node : graph.nodes()) {
            if (graph.predecessors(node).isEmpty()) {
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * 获取所有叶子节点（没有下游依赖的节点）
     */
    public Set<Long> getLeafNodes() {
        Set<Long> leaves = new HashSet<>();
        for (Long node : graph.nodes()) {
            if (graph.successors(node).isEmpty()) {
                leaves.add(node);
            }
        }
        return leaves;
    }

    /**
     * 获取节点的所有上游节点
     */
    public Set<Long> getPredecessors(Long nodeId) {
        return graph.predecessors(nodeId);
    }

    /**
     * 获取节点的所有下游节点
     */
    public Set<Long> getSuccessors(Long nodeId) {
        return graph.successors(nodeId);
    }

    /**
     * 拓扑排序 - 返回执行顺序
     */
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

        // 反转得到正确的执行顺序
        java.util.Collections.reverse(result);
        return result;
    }

    private void topologicalSortDfs(Long node, Set<Long> visited, Set<Long> visiting, List<Long> result) {
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

    /**
     * 获取节点数量
     */
    public int nodeCount() {
        return graph.nodes().size();
    }

    /**
     * 获取边数量
     */
    public int edgeCount() {
        return graph.edges().size();
    }
}
