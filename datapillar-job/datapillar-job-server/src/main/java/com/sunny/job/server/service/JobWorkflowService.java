package com.sunny.job.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunny.job.server.entity.JobWorkflow;

/**
 * 工作流定义 Service
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public interface JobWorkflowService extends IService<JobWorkflow> {

    /**
     * 上线工作流
     * <p>
     * 核心职责：创建首个 job_workflow_run + job_run + job_run_dependency
     *
     * @param workflowId 工作流ID
     */
    void online(Long workflowId);

    /**
     * 下线工作流
     *
     * @param workflowId 工作流ID
     */
    void offline(Long workflowId);
}
