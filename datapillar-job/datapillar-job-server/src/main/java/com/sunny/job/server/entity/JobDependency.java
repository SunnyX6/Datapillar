package com.sunny.job.server.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 任务依赖关系实体（设计阶段）
 * <p>
 * 定义 job_info 之间的依赖关系
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@TableName("job_dependency")
public class JobDependency {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workflowId;

    /**
     * 当前任务ID
     */
    private Long jobId;

    /**
     * 上游任务ID（依赖）
     */
    private Long parentJobId;

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

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Long getParentJobId() {
        return parentJobId;
    }

    public void setParentJobId(Long parentJobId) {
        this.parentJobId = parentJobId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
