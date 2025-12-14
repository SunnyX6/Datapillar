package com.sunny.job.worker.domain.entity;

/**
 * 告警规则实体
 * <p>
 * Worker 查询告警规则时使用（关联 job_alarm_rule + job_alarm_channel）
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class JobAlertRule {

    private Long ruleId;
    private Long workflowId;
    private Long jobId;
    private Integer triggerEvent;
    private Long alertChannelId;
    private Integer channelType;
    private String channelConfig;

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
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

    public Integer getTriggerEvent() {
        return triggerEvent;
    }

    public void setTriggerEvent(Integer triggerEvent) {
        this.triggerEvent = triggerEvent;
    }

    public Long getAlertChannelId() {
        return alertChannelId;
    }

    public void setAlertChannelId(Long alertChannelId) {
        this.alertChannelId = alertChannelId;
    }

    public Integer getChannelType() {
        return channelType;
    }

    public void setChannelType(Integer channelType) {
        this.channelType = channelType;
    }

    public String getChannelConfig() {
        return channelConfig;
    }

    public void setChannelConfig(String channelConfig) {
        this.channelConfig = channelConfig;
    }
}
