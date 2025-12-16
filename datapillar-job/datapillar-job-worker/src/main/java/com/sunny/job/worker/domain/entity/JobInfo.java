package com.sunny.job.worker.domain.entity;

/**
 * 任务定义实体
 * <p>
 * Worker 查询任务定义时使用
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class JobInfo {

    private Long id;
    private Long workflowId;
    private String jobName;
    private Long jobType;
    private String jobParams;
    private Integer routeStrategy;
    private Integer blockStrategy;
    private Integer timeoutSeconds;
    private Integer maxRetryTimes;
    private Integer retryInterval;
    private Integer priority;

    /**
     * 触发类型（NULL 继承工作流）: 1-CRON 2-固定频率 3-固定延迟
     */
    private Integer triggerType;

    /**
     * 触发值（CRON表达式或秒数）
     */
    private String triggerValue;

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

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public Long getJobType() {
        return jobType;
    }

    public void setJobType(Long jobType) {
        this.jobType = jobType;
    }

    public String getJobParams() {
        return jobParams;
    }

    public void setJobParams(String jobParams) {
        this.jobParams = jobParams;
    }

    public Integer getRouteStrategy() {
        return routeStrategy;
    }

    public void setRouteStrategy(Integer routeStrategy) {
        this.routeStrategy = routeStrategy;
    }

    public Integer getBlockStrategy() {
        return blockStrategy;
    }

    public void setBlockStrategy(Integer blockStrategy) {
        this.blockStrategy = blockStrategy;
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

    public Integer getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(Integer retryInterval) {
        this.retryInterval = retryInterval;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
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
}
