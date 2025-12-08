package com.sunny.job.admin.service;

import com.sunny.job.admin.dto.ProjectWorkflowSummaryDTO;
import com.sunny.job.admin.dto.WorkflowNodeDTO;
import com.sunny.job.admin.model.DatapillarJobDependency;
import com.sunny.job.admin.model.DatapillarJobInfo;
import com.sunny.job.admin.model.DatapillarJobWorkflow;
import com.sunny.job.core.biz.model.ReturnT;

import java.util.List;
import java.util.Map;

/**
 * datapillar-job workflow service
 * 负责工作流的CRUD管理和执行操作
 *
 * @author datapillar-job-admin
 * @date 2025-11-09
 */
public interface DatapillarJobWorkflowService {

    // ==================== CRUD操作 ====================

    /**
     * 创建工作流
     */
    ReturnT<DatapillarJobWorkflow> create(DatapillarJobWorkflow workflow);

    /**
     * 更新工作流
     */
    ReturnT<DatapillarJobWorkflow> update(DatapillarJobWorkflow workflow);

    /**
     * 删除工作流
     */
    ReturnT<String> delete(Long workflowId);

    /**
     * 根据ID查询工作流
     */
    DatapillarJobWorkflow getById(Long workflowId);

    /**
     * 根据项目ID查询工作流列表
     */
    List<DatapillarJobWorkflow> getByProjectId(Long projectId);

    /**
     * 根据文件夹ID查询工作流列表
     */
    List<DatapillarJobWorkflow> getByFolderId(Long folderId);

    /**
     * 根据名称搜索工作流
     */
    List<DatapillarJobWorkflow> searchByName(Long projectId, String name);

    /**
     * 查询所有工作流
     */
    List<DatapillarJobWorkflow> findAll();

    /**
     * 根据状态查询工作流
     */
    List<DatapillarJobWorkflow> findByStatus(String status);

    /**
     * 获取用户所有项目的工作流统计信息
     *
     * @param userId 用户ID
     * @return 项目工作流统计列表
     */
    List<ProjectWorkflowSummaryDTO> getUserProjectsSummary(Long userId);

    // ==================== 节点管理 ====================

    /**
     * 批量添加workflow节点
     *
     * @param workflowId 工作流ID
     * @param nodeDTOs   节点信息列表
     * @return nodeId到jobId的映射
     */
    ReturnT<Map<String, Integer>> addNodesBatch(Long workflowId, List<WorkflowNodeDTO> nodeDTOs);

    /**
     * 删除workflow节点
     *
     * @param workflowId 工作流ID
     * @param nodeId     节点ID（前端nodeId）
     * @return 删除结果
     */
    ReturnT<String> deleteNode(Long workflowId, String nodeId);

    /**
     * 查询workflow的所有节点
     *
     * @param workflowId 工作流ID
     * @return 节点列表
     */
    ReturnT<List<DatapillarJobInfo>> getNodes(Long workflowId);

    // ==================== 依赖管理 ====================

    /**
     * 批量添加依赖（基于前端连线）
     *
     * @param workflowId   工作流ID
     * @param dependencies 依赖关系列表，格式: ["nodeId1->nodeId2", "nodeId2->nodeId3"]
     * @param nodeToJobMap nodeId到jobId的映射
     * @return 添加结果
     */
    ReturnT<String> batchAddDependencies(Long workflowId, List<String> dependencies, Map<String, Integer> nodeToJobMap);

    /**
     * 删除依赖
     *
     * @param workflowId   工作流ID
     * @param dependencyId 依赖ID
     * @return 删除结果
     */
    ReturnT<String> deleteDependency(Long workflowId, Long dependencyId);

    /**
     * 查询workflow的所有依赖
     *
     * @param workflowId 工作流ID
     * @return 依赖列表
     */
    ReturnT<List<DatapillarJobDependency>> getDependencies(Long workflowId);

    // ==================== 执行操作 ====================

    /**
     * 启动工作流执行
     */
    ReturnT<String> startWorkflow(long workflowId);

    /**
     * 停止工作流
     */
    ReturnT<String> stopWorkflow(long workflowId);

    /**
     * 查询工作流详情(包含任务状态)
     */
    ReturnT<Map<String, Object>> getWorkflowDetail(long workflowId);

    /**
     * 获取工作流的可执行任务
     */
    ReturnT<List<Integer>> getReadyTasks(long workflowId);

    /**
     * 重新运行整个工作流
     */
    ReturnT<String> rerunWorkflow(long workflowId);

    /**
     * 重新运行单个任务
     */
    ReturnT<String> retryTask(long workflowId, int jobId);

    /**
     * 运行单个任务（返回任务状态）
     */
    ReturnT<DatapillarJobInfo> runTask(long workflowId, int jobId);

    /**
     * 从某个节点开始重跑
     */
    ReturnT<String> rerunFromTask(long workflowId, int startJobId);
}
