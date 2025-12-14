package com.sunny.job.server.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 跨工作流依赖关系实体
 * <p>
 * 当前工作流依赖另一个工作流中的某个 job 在同一调度周期内成功
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@TableName("job_workflow_dependency")
public class JobWorkflowDependency {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 当前工作流ID
     */
    private Long workflowId;

    /**
     * 依赖的工作流ID
     */
    private Long dependWorkflowId;

    /**
     * 依赖的具体任务ID
     */
    private Long dependJobId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
