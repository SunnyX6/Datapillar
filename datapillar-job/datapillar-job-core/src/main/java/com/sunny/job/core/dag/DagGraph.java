package com.sunny.job.core.dag;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.sunny.job.core.common.Assert;
import com.sunny.job.core.common.Constants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 Guava Graph 的 DAG 封装
 * <p>
 * 提供建图、校验、拓扑序、就绪集计算等功能
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public final class DagGraph {

    private final MutableGraph<Long> graph;
    private final Map<Long, Map<Long, DagEdge>> edgeMap;

    private DagGraph() {
        this.graph = GraphBuilder.directed()
                .allowsSelfLoops(false)
                .build();
        this.edgeMap = new HashMap<>();
    }

    /**
     * 创建空 DAG
     */
    public static DagGraph create() {
        return new DagGraph();
    }

    /**
     * 从节点和边构建 DAG
     *
     * @param nodeIds 节点 ID 集合
     * @param edges   边集合
     * @return DAG 实例
     * @throws DagValidationException 如果存在环或无效边
     */
    public static DagGraph from(Collection<Long> nodeIds, Collection<DagEdge> edges) {
        Assert.notNull(nodeIds, "节点集合不能为空");

        if (nodeIds.size() > Constants.DAG_MAX_NODES) {
            throw new DagValidationException("DAG 节点数超过最大限制: " + Constants.DAG_MAX_NODES);
        }

        DagGraph dag = new DagGraph();

        for (Long nodeId : nodeIds) {
            Assert.notNull(nodeId, "节点 ID 不能为空");
            Assert.isTrue(nodeId > 0, "节点 ID 必须大于 0");
            dag.graph.addNode(nodeId);
        }

        if (edges != null) {
            for (DagEdge edge : edges) {
                long from = edge.getFromNodeId();
                long to = edge.getToNodeId();

                if (!dag.graph.nodes().contains(from)) {
                    throw new DagValidationException("边引用了不存在的源节点: " + from);
                }
                if (!dag.graph.nodes().contains(to)) {
                    throw new DagValidationException("边引用了不存在的目标节点: " + to);
                }

                dag.graph.putEdge(from, to);
                dag.edgeMap.computeIfAbsent(from, k -> new HashMap<>()).put(to, edge);
            }
        }

        dag.validateAcyclic();
        return dag;
    }

    /**
     * 添加节点
     */
    public void addNode(long nodeId) {
        Assert.isTrue(nodeId > 0, "节点 ID 必须大于 0");
        if (graph.nodes().size() >= Constants.DAG_MAX_NODES) {
            throw new DagValidationException("DAG 节点数超过最大限制: " + Constants.DAG_MAX_NODES);
        }
        graph.addNode(nodeId);
    }

    /**
     * 添加边
     */
    public void addEdge(DagEdge edge) {
        Assert.notNull(edge, "边不能为空");

        long from = edge.getFromNodeId();
        long to = edge.getToNodeId();

        if (!graph.nodes().contains(from)) {
            throw new DagValidationException("边引用了不存在的源节点: " + from);
        }
        if (!graph.nodes().contains(to)) {
            throw new DagValidationException("边引用了不存在的目标节点: " + to);
        }

        graph.putEdge(from, to);
        edgeMap.computeIfAbsent(from, k -> new HashMap<>()).put(to, edge);
    }

    /**
     * 验证 DAG 无环
     *
     * @throws DagValidationException 如果存在环
     */
    public void validateAcyclic() {
        if (hasCycle()) {
            throw new DagValidationException("DAG 存在循环依赖");
        }
    }

    /**
     * 检测是否存在环 (Kahn 算法)
     */
    public boolean hasCycle() {
        if (graph.nodes().isEmpty()) {
            return false;
        }

        Map<Long, Integer> inDegree = new HashMap<>();
        for (Long node : graph.nodes()) {
            inDegree.put(node, graph.inDegree(node));
        }

        Queue<Long> queue = new ArrayDeque<>();
        inDegree.forEach((node, degree) -> {
            if (degree == 0) {
                queue.add(node);
            }
        });

        int visited = 0;
        while (!queue.isEmpty()) {
            Long node = queue.poll();
            visited++;
            for (Long successor : graph.successors(node)) {
                int newDegree = inDegree.compute(successor, (k, v) -> v - 1);
                if (newDegree == 0) {
                    queue.add(successor);
                }
            }
        }

        return visited != graph.nodes().size();
    }

    /**
     * 返回拓扑排序
     *
     * @return 拓扑序节点列表
     * @throws DagValidationException 如果存在环
     */
    public List<Long> topologicalSort() {
        if (graph.nodes().isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Integer> inDegree = new HashMap<>();
        for (Long node : graph.nodes()) {
            inDegree.put(node, graph.inDegree(node));
        }

        Queue<Long> queue = new ArrayDeque<>();
        inDegree.forEach((node, degree) -> {
            if (degree == 0) {
                queue.add(node);
            }
        });

        List<Long> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Long node = queue.poll();
            result.add(node);
            for (Long successor : graph.successors(node)) {
                int newDegree = inDegree.compute(successor, (k, v) -> v - 1);
                if (newDegree == 0) {
                    queue.add(successor);
                }
            }
        }

        if (result.size() != graph.nodes().size()) {
            throw new DagValidationException("DAG 存在循环依赖，无法完成拓扑排序");
        }

        return result;
    }

    /**
     * 计算当前就绪节点
     * <p>
     * 就绪条件：所有前置节点都已完成，且自身不在运行中
     *
     * @param completed 已完成节点集合
     * @param running   运行中节点集合
     * @return 就绪节点集合
     */
    public Set<Long> computeReadyNodes(Set<Long> completed, Set<Long> running) {
        Set<Long> ready = new HashSet<>();

        for (Long node : graph.nodes()) {
            if (completed.contains(node) || running.contains(node)) {
                continue;
            }

            boolean allPredecessorsCompleted = graph.predecessors(node)
                    .stream()
                    .allMatch(completed::contains);

            if (allPredecessorsCompleted) {
                ready.add(node);
            }
        }

        return ready;
    }

    /**
     * 获取根节点 (入度为 0)
     */
    public Set<Long> getRootNodes() {
        return graph.nodes().stream()
                .filter(node -> graph.inDegree(node) == 0)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 获取叶子节点 (出度为 0)
     */
    public Set<Long> getLeafNodes() {
        return graph.nodes().stream()
                .filter(node -> graph.outDegree(node) == 0)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 获取节点的前置节点
     */
    public Set<Long> getPredecessors(long nodeId) {
        if (!graph.nodes().contains(nodeId)) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(graph.predecessors(nodeId));
    }

    /**
     * 获取节点的后继节点
     */
    public Set<Long> getSuccessors(long nodeId) {
        if (!graph.nodes().contains(nodeId)) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(graph.successors(nodeId));
    }

    /**
     * 获取边信息
     */
    public DagEdge getEdge(long fromNodeId, long toNodeId) {
        Map<Long, DagEdge> edges = edgeMap.get(fromNodeId);
        return edges != null ? edges.get(toNodeId) : null;
    }

    /**
     * 获取所有节点
     */
    public Set<Long> getNodes() {
        return Collections.unmodifiableSet(graph.nodes());
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

    /**
     * 是否为空图
     */
    public boolean isEmpty() {
        return graph.nodes().isEmpty();
    }

    /**
     * 检查是否所有节点都已完成
     */
    public boolean isAllCompleted(Set<Long> completed) {
        return completed.containsAll(graph.nodes());
    }

    @Override
    public String toString() {
        return "DagGraph{nodes=" + nodeCount() + ", edges=" + edgeCount() + "}";
    }
}
