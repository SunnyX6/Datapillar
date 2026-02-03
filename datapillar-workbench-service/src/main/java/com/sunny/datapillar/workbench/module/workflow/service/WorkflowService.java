package com.sunny.datapillar.workbench.module.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.workbench.module.workflow.dto.WorkflowDto;

/**
 * 工作流服务接口
 *
 * @author sunny
 */
public interface WorkflowService {

    // ==================== 工作流 CRUD ====================

    /**
     * 分页查询工作流列表
     */
    IPage<WorkflowDto.ListItem> getWorkflowPage(Page<WorkflowDto.ListItem> page, Long projectId, String workflowName, Integer status);

    /**
     * 获取工作流详情（含任务和依赖）
     */
    WorkflowDto.Response getWorkflowDetail(Long id);

    /**
     * 创建工作流
     */
    Long createWorkflow(WorkflowDto.Create dto);

    /**
     * 更新工作流
     */
    void updateWorkflow(Long id, WorkflowDto.Update dto);

    /**
     * 删除工作流（同时删除Airflow DAG）
     */
    void deleteWorkflow(Long id);

    // ==================== DAG 管理 ====================

    /**
     * 发布工作流到 Airflow
     */
    void publishWorkflow(Long id);

    /**
     * 暂停工作流
     */
    void pauseWorkflow(Long id);

    /**
     * 恢复工作流
     */
    void resumeWorkflow(Long id);

    /**
     * 获取DAG详情（从Airflow）
     */
    JsonNode getDagDetail(Long id);

    /**
     * 获取DAG版本列表
     */
    JsonNode getDagVersions(Long id, int limit, int offset);

    /**
     * 获取DAG特定版本详情
     */
    JsonNode getDagVersion(Long id, int versionNumber);

    // ==================== DAG Run 管理 ====================

    /**
     * 触发工作流运行
     */
    JsonNode triggerWorkflow(Long id, WorkflowDto.TriggerRequest request);

    /**
     * 获取运行列表
     */
    JsonNode getWorkflowRuns(Long id, int limit, int offset, String state);

    /**
     * 获取运行详情
     */
    JsonNode getWorkflowRun(Long id, String runId);

    // ==================== Job 管理 ====================

    /**
     * 获取任务列表
     */
    JsonNode getRunJobs(Long id, String runId);

    /**
     * 获取任务详情
     */
    JsonNode getRunJob(Long id, String runId, String jobId);

    /**
     * 获取任务日志
     */
    JsonNode getJobLogs(Long id, String runId, String jobId, int tryNumber);

    /**
     * 重跑任务
     */
    JsonNode rerunJob(Long id, String runId, String jobId, WorkflowDto.RerunJobRequest request);

    /**
     * 设置任务状态
     */
    JsonNode setJobState(Long id, String runId, String jobId, WorkflowDto.SetJobStateRequest request);

    /**
     * 批量清除任务
     */
    JsonNode clearJobs(Long id, String runId, WorkflowDto.ClearJobsRequest request);
}
