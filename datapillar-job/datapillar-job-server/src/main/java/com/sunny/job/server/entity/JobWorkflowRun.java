package com.sunny.job.server.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 工作流执行实例实体
 * <p>
 * Workflow 上线时生成，代表一次调度周期的执行
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@TableName("job_workflow_run")
public class JobWorkflowRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long namespaceId;

    private Long workflowId;

    /**
     * 触发类型: 1-CRON 2-固定频率 3-固定延迟 4-手动 5-API
     */
    private Integer triggerType;

    /**
     * 计划触发时间（毫秒）
     */
    private Long triggerTime;

    /**
     * 状态: 0-等待 1-运行中 2-成功 3-失败 4-取消 5-超时
     */
    private Integer status;

    /**
     * 实际开始时间（毫秒）
     */
    private Long startTime;

    /**
     * 结束时间（毫秒）
     */
    private Long endTime;

    /**
     * 执行结果/错误信息
     */
    private String resultMessage;

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

    public Long getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(Long workflowId) {
        this.workflowId = workflowId;
    }

    public Integer getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(Integer triggerType) {
        this.triggerType = triggerType;
    }

    public Long getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(Long triggerTime) {
        this.triggerTime = triggerTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public void setResultMessage(String resultMessage) {
        this.resultMessage = resultMessage;
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
}
