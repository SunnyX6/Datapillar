package com.sunny.job.server.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 任务执行依赖关系实体（执行阶段）
 * <p>
 * 统一存储 job_run 之间的依赖关系（工作流内 + 跨工作流）
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@TableName("job_run_dependency")
public class JobRunDependency {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 当前任务所属的工作流执行实例ID
     */
    private Long workflowRunId;

    /**
     * 当前任务实例ID
     */
    private Long jobRunId;

    /**
     * 依赖的任务实例ID（可同一workflow，可跨workflow）
     */
    private Long parentRunId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
