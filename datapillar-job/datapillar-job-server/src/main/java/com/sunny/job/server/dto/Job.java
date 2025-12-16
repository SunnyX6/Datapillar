package com.sunny.job.server.dto;

/**
 * 任务 DTO
 *
 * @author SunnyX6
 * @date 2025-12-16
 */
public class Job {

    private Long id;

    private String jobName;

    private Long jobType;

    private String jobParams;

    private Integer routeStrategy;

    private Integer blockStrategy;

    private Integer timeoutSeconds;

    private Integer maxRetryTimes;

    private Integer retryInterval;

    private Integer priority;

    private Integer triggerType;

    private String triggerValue;

    private String description;

    private Double positionX;

    private Double positionY;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getPositionX() {
        return positionX;
    }

    public void setPositionX(Double positionX) {
        this.positionX = positionX;
    }

    public Double getPositionY() {
        return positionY;
    }

    public void setPositionY(Double positionY) {
        this.positionY = positionY;
    }
}
