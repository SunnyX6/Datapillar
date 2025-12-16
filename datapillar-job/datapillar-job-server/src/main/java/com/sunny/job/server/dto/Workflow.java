package com.sunny.job.server.dto;

import com.sunny.job.server.entity.JobWorkflow;

/**
 * 工作流 DTO
 *
 * @author SunnyX6
 * @date 2025-12-16
 */
public class Workflow {

    private Long id;

    private Long namespaceId;

    private String workflowName;

    private Integer triggerType;

    private String triggerValue;

    private Integer timeoutSeconds;

    private Integer maxRetryTimes;

    private Integer priority;

    private Integer status;

    private String description;

    public static Workflow from(JobWorkflow entity) {
        Workflow dto = new Workflow();
        dto.setId(entity.getId());
        dto.setNamespaceId(entity.getNamespaceId());
        dto.setWorkflowName(entity.getWorkflowName());
        dto.setTriggerType(entity.getTriggerType());
        dto.setTriggerValue(entity.getTriggerValue());
        dto.setTimeoutSeconds(entity.getTimeoutSeconds());
        dto.setMaxRetryTimes(entity.getMaxRetryTimes());
        dto.setPriority(entity.getPriority());
        dto.setStatus(entity.getStatus());
        dto.setDescription(entity.getDescription());
        return dto;
    }

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
