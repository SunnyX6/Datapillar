package com.sunny.job.server.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 告警规则实体
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@TableName("job_alarm_rule")
public class JobAlarmRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long namespaceId;

    private String ruleName;

    /**
     * 关联的任务ID（与workflowId互斥）
     */
    private Long jobId;

    /**
     * 关联的工作流ID（与jobId互斥）
     */
    private Long workflowId;

    /**
     * 触发事件: 1-失败 2-超时 3-成功
     */
    private Integer triggerEvent;

    /**
     * 连续失败N次触发告警
     */
    private Integer failThreshold;

    /**
     * 恢复时是否通知: 0-否 1-是
     */
    private Integer notifyOnRecover;

    /**
     * 告警渠道ID列表（逗号分隔）
     */
    private String channelIds;

    /**
     * 当前连续失败次数
     */
    private Integer consecutiveFails;

    /**
     * 告警状态: 0-正常 1-已触发
     */
    private Integer alarmStatus;

    /**
     * 上次触发时间（毫秒）
     */
    private Long lastTriggerTime;

    /**
     * 规则状态: 0-禁用 1-启用
     */
    private Integer ruleStatus;

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

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Long getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(Long workflowId) {
        this.workflowId = workflowId;
    }

    public Integer getTriggerEvent() {
        return triggerEvent;
    }

    public void setTriggerEvent(Integer triggerEvent) {
        this.triggerEvent = triggerEvent;
    }

    public Integer getFailThreshold() {
        return failThreshold;
    }

    public void setFailThreshold(Integer failThreshold) {
        this.failThreshold = failThreshold;
    }

    public Integer getNotifyOnRecover() {
        return notifyOnRecover;
    }

    public void setNotifyOnRecover(Integer notifyOnRecover) {
        this.notifyOnRecover = notifyOnRecover;
    }

    public String getChannelIds() {
        return channelIds;
    }

    public void setChannelIds(String channelIds) {
        this.channelIds = channelIds;
    }

    public Integer getConsecutiveFails() {
        return consecutiveFails;
    }

    public void setConsecutiveFails(Integer consecutiveFails) {
        this.consecutiveFails = consecutiveFails;
    }

    public Integer getAlarmStatus() {
        return alarmStatus;
    }

    public void setAlarmStatus(Integer alarmStatus) {
        this.alarmStatus = alarmStatus;
    }

    public Long getLastTriggerTime() {
        return lastTriggerTime;
    }

    public void setLastTriggerTime(Long lastTriggerTime) {
        this.lastTriggerTime = lastTriggerTime;
    }

    public Integer getRuleStatus() {
        return ruleStatus;
    }

    public void setRuleStatus(Integer ruleStatus) {
        this.ruleStatus = ruleStatus;
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
}
