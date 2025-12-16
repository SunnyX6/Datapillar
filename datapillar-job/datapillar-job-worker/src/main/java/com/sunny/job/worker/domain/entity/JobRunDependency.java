package com.sunny.job.worker.domain.entity;

/**
 * 任务执行依赖关系实体
 * <p>
 * Worker 创建任务执行依赖关系时使用
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class JobRunDependency {

    private Long id;
    private Long workflowRunId;
    private Long jobRunId;
    private Long parentRunId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkflowRunId() {
        return workflowRunId;
    }

    public void setWorkflowRunId(Long workflowRunId) {
        this.workflowRunId = workflowRunId;
    }

    public Long getJobRunId() {
        return jobRunId;
    }

    public void setJobRunId(Long jobRunId) {
        this.jobRunId = jobRunId;
    }

    public Long getParentRunId() {
        return parentRunId;
    }

    public void setParentRunId(Long parentRunId) {
        this.parentRunId = parentRunId;
    }
}
