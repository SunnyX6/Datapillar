package com.sunny.job.worker.domain.entity;

/**
 * 跨工作流依赖实体
 * <p>
 * Worker 查询跨工作流依赖时使用
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class JobWorkflowDependency {

    private Long workflowId;
    private Long dependWorkflowId;
    private Long dependJobId;

    public Long getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(Long workflowId) {
        this.workflowId = workflowId;
    }

    public Long getDependWorkflowId() {
        return dependWorkflowId;
    }

    public void setDependWorkflowId(Long dependWorkflowId) {
        this.dependWorkflowId = dependWorkflowId;
    }

    public Long getDependJobId() {
        return dependJobId;
    }

    public void setDependJobId(Long dependJobId) {
        this.dependJobId = dependJobId;
    }
}
