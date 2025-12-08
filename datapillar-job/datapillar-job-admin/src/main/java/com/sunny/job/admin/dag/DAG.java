package com.sunny.job.admin.dag;

import java.util.*;

/**
 * DAG图数据结构
 *
 * @author datapillar-job-admin
 * @date 2025-11-06
 */
public class DAG {

    /**
     * 邻接表：key=节点ID，value=该节点的所有前驱节点列表
     */
    private Map<Integer, List<Integer>> adjacencyList;

    /**
     * 所有节点ID列表
     */
    private List<Integer> nodes;

    public DAG(Map<Integer, List<Integer>> adjacencyList, List<Integer> nodes) {
        this.adjacencyList = adjacencyList;
        this.nodes = nodes;
    }

    /**
     * 使用Kahn算法进行拓扑排序
     *
     * @return 拓扑排序后的节点列表
     * @throws CycleDetectedException 如果检测到环则抛出异常
     */
    public List<Integer> topologicalSort() throws CycleDetectedException {
        // 计算每个节点的入度
        Map<Integer, Integer> inDegree = new HashMap<>();
        for (Integer node : nodes) {
            inDegree.put(node, 0);
        }

        // 统计入度
        for (Map.Entry<Integer, List<Integer>> entry : adjacencyList.entrySet()) {
            Integer node = entry.getKey();
            List<Integer> predecessors = entry.getValue();
            inDegree.put(node, predecessors.size());
        }

        // 找出所有入度为0的节点（起始节点）
        Queue<Integer> queue = new LinkedList<>();
        for (Map.Entry<Integer, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        // Kahn算法核心：逐步移除入度为0的节点
        List<Integer> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Integer current = queue.poll();
            result.add(current);

            // 找到所有依赖当前节点的后继节点
            for (Map.Entry<Integer, List<Integer>> entry : adjacencyList.entrySet()) {
                Integer node = entry.getKey();
                List<Integer> predecessors = entry.getValue();

                if (predecessors.contains(current)) {
                    // 将当前节点从前驱列表中移除（相当于删除边）
                    int currentInDegree = inDegree.get(node);
                    inDegree.put(node, currentInDegree - 1);

                    // 如果入度变为0,加入队列
                    if (inDegree.get(node) == 0) {
                        queue.offer(node);
                    }
                }
            }
        }

        // 如果排序结果数量少于节点总数，说明存在环
        if (result.size() < nodes.size()) {
            throw new CycleDetectedException("检测到循环依赖！已排序节点数: " + result.size() + ", 总节点数: " + nodes.size());
        }

        return result;
    }

    /**
     * 获取指定节点的所有前驱节点（直接依赖）
     */
    public List<Integer> getPredecessors(Integer nodeId) {
        return adjacencyList.getOrDefault(nodeId, new ArrayList<>());
    }

    /**
     * 获取指定节点的所有后继节点（依赖该节点的节点）
     */
    public List<Integer> getSuccessors(Integer nodeId) {
        List<Integer> successors = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : adjacencyList.entrySet()) {
            if (entry.getValue().contains(nodeId)) {
                successors.add(entry.getKey());
            }
        }
        return successors;
    }

    /**
     * 获取所有节点
     */
    public List<Integer> getNodes() {
        return new ArrayList<>(nodes);
    }

    /**
     * 获取邻接表
     */
    public Map<Integer, List<Integer>> getAdjacencyList() {
        return new HashMap<>(adjacencyList);
    }

    /**
     * 获取节点数量
     */
    public int getNodeCount() {
        return nodes.size();
    }

    /**
     * 检查是否包含指定节点
     */
    public boolean containsNode(Integer nodeId) {
        return nodes.contains(nodeId);
    }
}
