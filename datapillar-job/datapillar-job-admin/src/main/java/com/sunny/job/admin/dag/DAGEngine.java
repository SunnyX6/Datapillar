package com.sunny.job.admin.dag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sunny.job.admin.mapper.DatapillarJobDependencyMapper;
import com.sunny.job.admin.mapper.DatapillarJobInfoMapper;
import com.sunny.job.admin.model.DatapillarJobDependency;
import com.sunny.job.admin.model.DatapillarJobInfo;

import jakarta.annotation.Resource;

/**
 * DAG依赖引擎
 *
 * @author datapillar-job-admin
 * @date 2025-11-06
 */
@Component
public class DAGEngine {
    private static Logger logger = LoggerFactory.getLogger(DAGEngine.class);

    @Resource
    private DatapillarJobDependencyMapper dependencyMapper;

    @Resource
    private DatapillarJobInfoMapper jobInfoMapper;

    /**
     * 为指定workflow构建DAG图
     * 只包含该workflow内job之间的依赖关系
     */
    public DAG buildDAGForWorkflow(long workflowId) {
        // 获取workflow的所有job
        List<DatapillarJobInfo> jobs = jobInfoMapper.findByWorkflowId(workflowId);
        Set<Integer> jobIdSet = new HashSet<>();
        for (DatapillarJobInfo job : jobs) {
            jobIdSet.add(job.getId());
        }

        // 优化: 只查询该workflow的依赖关系,避免全表扫描
        List<DatapillarJobDependency> workflowDependencies = dependencyMapper.findByWorkflowId(workflowId);

        // 构建邻接表
        Map<Integer, List<Integer>> adjacencyList = new HashMap<>();
        for (DatapillarJobDependency dep : workflowDependencies) {
            adjacencyList.computeIfAbsent(dep.getToJobId(), k -> new ArrayList<>())
                    .add(dep.getFromJobId());
        }

        return new DAG(adjacencyList, new ArrayList<>(jobIdSet));
    }

    /**
     * 对指定workflow进行拓扑排序，检测循环依赖
     */
    public List<Integer> topologicalSortForWorkflow(long workflowId) throws CycleDetectedException {
        DAG dag = buildDAGForWorkflow(workflowId);
        return dag.topologicalSort();
    }

    /**
     * 获取任务的所有前置依赖
     */
    public List<DatapillarJobDependency> getDependencies(int jobId) {
        return dependencyMapper.findByToJobId(jobId);
    }

    /**
     * 获取任务的所有下游任务（依赖该任务的任务）
     */
    public List<DatapillarJobDependency> getDependents(int jobId) {
        return dependencyMapper.findByFromJobId(jobId);
    }

    /**
     * 递归获取所有下游任务ID(包括直接和间接依赖)
     * 使用DFS遍历,返回按执行顺序排列的下游任务列表
     */
    public List<Integer> getAllDownstreamJobs(int startJobId, long workflowId) {
        List<Integer> result = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        dfsDownstream(startJobId, workflowId, visited, result);
        return result;
    }

    /**
     * DFS遍历下游任务
     */
    private void dfsDownstream(int jobId, long workflowId, Set<Integer> visited, List<Integer> result) {
        if (visited.contains(jobId)) {
            return;
        }
        visited.add(jobId);

        List<DatapillarJobDependency> dependents = getDependents(jobId);
        for (DatapillarJobDependency dep : dependents) {
            if (dep.getWorkflowId() == workflowId) {
                int downstreamJobId = dep.getToJobId();
                dfsDownstream(downstreamJobId, workflowId, visited, result);
                if (!result.contains(downstreamJobId)) {
                    result.add(downstreamJobId);
                }
            }
        }
    }

    /**
     * 检查依赖是否满足
     */
    public boolean isDependencySatisfied(DatapillarJobDependency dependency, long workflowId) {
        DatapillarJobInfo depJobInfo = jobInfoMapper.loadByWorkflowAndJob(
                workflowId, dependency.getFromJobId());

        if (depJobInfo == null) {
            return false;
        }

        String dependencyType = dependency.getDependencyType();
        String status = depJobInfo.getStatus();

        switch (dependencyType) {
            case "SUCCESS":
                return "COMPLETED".equals(status);
            case "FAILURE":
                return "FAILED".equals(status);
            case "COMPLETE":
                return Arrays.asList("COMPLETED", "FAILED", "SKIPPED").contains(status);
            default:
                return false;
        }
    }

    /**
     * 检查是否所有依赖都已满足
     */
    public boolean allDependenciesSatisfied(int jobId, long workflowId) {
        List<DatapillarJobDependency> dependencies = getDependencies(jobId);

        if (dependencies == null || dependencies.isEmpty()) {
            return true;
        }

        // 优化: 批量查询所有前置任务的状态,避免N次单独查询
        List<Integer> fromJobIds = new ArrayList<>();
        for (DatapillarJobDependency dep : dependencies) {
            fromJobIds.add(dep.getFromJobId());
        }

        List<DatapillarJobInfo> jobInfoList = jobInfoMapper.loadByWorkflowAndJobs(workflowId, fromJobIds);

        // 构建 jobId -> JobInfo 映射
        Map<Integer, DatapillarJobInfo> jobInfoMap = new HashMap<>();
        for (DatapillarJobInfo jobInfo : jobInfoList) {
            jobInfoMap.put(jobInfo.getId(), jobInfo);
        }

        // 检查所有依赖是否满足
        for (DatapillarJobDependency dep : dependencies) {
            DatapillarJobInfo depJobInfo = jobInfoMap.get(dep.getFromJobId());

            if (depJobInfo == null) {
                return false;
            }

            String dependencyType = dep.getDependencyType();
            String status = depJobInfo.getStatus();

            boolean satisfied = false;
            switch (dependencyType) {
                case "SUCCESS":
                    satisfied = "COMPLETED".equals(status);
                    break;
                case "FAILURE":
                    satisfied = "FAILED".equals(status);
                    break;
                case "COMPLETE":
                    satisfied = Arrays.asList("COMPLETED", "FAILED", "SKIPPED").contains(status);
                    break;
            }

            if (!satisfied) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查依赖关系是否存在
     */
    public boolean dependencyExists(int fromJobId, int toJobId) {
        DatapillarJobDependency dependency = dependencyMapper.findByFromAndToJobId(fromJobId, toJobId);
        return dependency != null;
    }

    /**
     * 删除任务的所有依赖关系
     */
    public int deleteJobDependencies(int jobId) {
        return dependencyMapper.deleteByJobId(jobId);
    }

    /**
     * 验证工作流是否存在循环依赖（基于JSON数据，不访问数据库）
     * 在保存工作流到数据库之前调用此方法进行验证
     *
     * @param nodes 节点JSON数组
     * @param edges 边JSON数组
     * @throws CycleDetectedException 如果检测到循环依赖
     */
    public void validateDAG(JSONArray nodes, JSONArray edges) throws CycleDetectedException {
        if (nodes == null || nodes.isEmpty()) {
            logger.info("节点列表为空，跳过DAG验证");
            return;
        }

        // 构建邻接表（纯内存操作，不访问数据库）
        Map<String, List<String>> adjacencyList = new HashMap<>();
        List<String> nodeIds = new ArrayList<>();

        // 收集所有节点ID（包含 start/end 节点，确保完整的循环检测）
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            String nodeId = node.getString("id");
            nodeIds.add(nodeId);
            adjacencyList.put(nodeId, new ArrayList<>());
        }

        // 如果没有节点，跳过验证
        if (nodeIds.isEmpty()) {
            logger.info("没有节点，跳过DAG验证");
            return;
        }

        // 构建边关系（target的依赖列表）
        if (edges != null && !edges.isEmpty()) {
            for (int i = 0; i < edges.size(); i++) {
                JSONObject edge = edges.getJSONObject(i);
                String source = edge.getString("source");
                String target = edge.getString("target");

                // 构建所有边的依赖关系（包含 start/end）
                if (adjacencyList.containsKey(target)) {
                    List<String> dependencies = adjacencyList.get(target);
                    dependencies.add(source);
                }
            }
        }

        logger.info("开始验证DAG拓扑结构: 节点数={}, 边数={}", nodeIds.size(), edges != null ? edges.size() : 0);

        // 使用Kahn算法进行拓扑排序验证
        // 如果存在循环依赖，会抛出CycleDetectedException
        Map<String, Integer> inDegree = new HashMap<>();
        for (String nodeId : nodeIds) {
            inDegree.put(nodeId, 0);
        }

        // 计算入度
        for (Map.Entry<String, List<String>> entry : adjacencyList.entrySet()) {
            String target = entry.getKey();
            List<String> sources = entry.getValue();
            inDegree.put(target, sources.size());
        }

        // Kahn算法：找出所有入度为0的节点
        List<String> queue = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sortedNodes = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.remove(0);
            sortedNodes.add(current);

            // 遍历所有依赖当前节点的节点
            for (Map.Entry<String, List<String>> entry : adjacencyList.entrySet()) {
                String target = entry.getKey();
                List<String> sources = entry.getValue();

                if (sources.contains(current)) {
                    int newDegree = inDegree.get(target) - 1;
                    inDegree.put(target, newDegree);

                    if (newDegree == 0) {
                        queue.add(target);
                    }
                }
            }
        }

        // 如果排序后的节点数小于总节点数，说明存在循环
        if (sortedNodes.size() != nodeIds.size()) {
            // 找出参与循环的节点
            Set<String> sortedSet = new HashSet<>(sortedNodes);
            List<String> cycleNodes = new ArrayList<>();
            for (String nodeId : nodeIds) {
                if (!sortedSet.contains(nodeId)) {
                    cycleNodes.add(nodeId);
                }
            }

            String errorMsg = String.format("检测到循环依赖！参与循环的节点: %s", cycleNodes);
            logger.error(errorMsg);
            throw new CycleDetectedException(errorMsg);
        }

        logger.info("DAG验证通过，拓扑排序结果: {}", sortedNodes);
    }
}