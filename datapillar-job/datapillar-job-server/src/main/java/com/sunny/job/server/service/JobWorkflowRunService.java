package com.sunny.job.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunny.job.server.entity.JobWorkflowRun;

/**
 * 工作流执行实例 Service
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public interface JobWorkflowRunService extends IService<JobWorkflowRun> {

    /**
     * 重跑工作流实例
     * <p>
     * 重置失败的 job_run 状态为 WAITING
     *
     * @param workflowRunId 工作流执行实例ID
     */
    void rerun(Long workflowRunId);
}
