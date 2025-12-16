package com.sunny.job.server.service;

import java.util.Map;

/**
 * 工作流运行实例 Service
 * <p>
 * 处理运行实例级别的操作：kill、rerun
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public interface JobWorkflowRunService {

    /**
     * 终止工作流运行实例
     *
     * @param workflowRunId 工作流运行实例ID
     */
    void kill(Long workflowRunId);

    /**
     * 重跑工作流运行实例
     * <p>
     * 重置失败的任务状态为 WAITING，重新执行
     *
     * @param workflowId          工作流ID
     * @param workflowRunId       工作流运行实例ID
     * @param jobRunIdToJobIdMap  需要重跑的任务映射（jobRunId -> jobId）
     */
    void rerun(Long workflowId, Long workflowRunId, Map<Long, Long> jobRunIdToJobIdMap);
}
