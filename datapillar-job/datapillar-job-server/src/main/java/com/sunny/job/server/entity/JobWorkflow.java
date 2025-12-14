package com.sunny.job.server.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 工作流定义实体
 * <p>
 * 工作流是调度的最小单位，定义触发策略
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@TableName("job_workflow")
public class JobWorkflow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long namespaceId;

    private String workflowName;

    /**
     * 状态: 0-下线 1-上线
     */
    private Integer workflowStatus;

    /**
     * 触发类型: 1-CRON 2-固定频率 3-固定延迟 4-手动 5-API
     */
    private Integer triggerType;

    /**
     * 触发配置（CRON表达式或秒数）
     */
    private String triggerValue;

    /**
     * 整体超时（秒）0-不限制
     */
    private Integer timeoutSeconds;

    /**
     * 失败重试次数
     */
    private Integer maxRetryTimes;

    /**
     * 优先级: 数字越大越优先
     */
    private Integer priority;

    private String description;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(Long namespaceId) {
        this.namespaceId = namespaceId;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public Integer getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(Integer workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public Integer getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(Integer triggerType) {
        this.triggerType = triggerType;
    }

    public String getTriggerValue() {
        return triggerValue;
    }

    public void setTriggerValue(String triggerValue) {
        this.triggerValue = triggerValue;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getMaxRetryTimes() {
        return maxRetryTimes;
    }

    public void setMaxRetryTimes(Integer maxRetryTimes) {
        this.maxRetryTimes = maxRetryTimes;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 是否已上线
     */
    public boolean isOnline() {
        return workflowStatus != null && workflowStatus == 1;
    }
}
