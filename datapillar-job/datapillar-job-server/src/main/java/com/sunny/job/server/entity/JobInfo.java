package com.sunny.job.server.entity;

import com.baomidou.mybatisplus.annotation.*;

/**
 * 任务定义实体
 * <p>
 * 任务必须属于某个工作流
 * 纯配置表，无状态字段
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@TableName("job_info")
public class JobInfo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long namespaceId;

    private Long workflowId;

    private String jobName;

    /**
     * 任务类型: 关联 job_component.id
     */
    private Long jobType;

    /**
     * 任务配置（JSON格式，不同类型结构不同）
     */
    private String jobParams;

    /**
     * 路由策略: 1-FIRST 2-ROUND_ROBIN 3-RANDOM 4-HASH 5-LEAST_BUSY 6-FAILOVER 7-SHARDING
     */
    private Integer routeStrategy;

    /**
     * 阻塞策略: 1-丢弃后续 2-覆盖之前 3-并行执行
     */
    private Integer blockStrategy;

    /**
     * 执行超时（秒）0-不限制
     */
    private Integer timeoutSeconds;

    /**
     * 失败重试次数
     */
    private Integer maxRetryTimes;

    /**
     * 重试间隔（秒）
     */
    private Integer retryInterval;

    /**
     * 优先级: 数字越大越优先
     */
    private Integer priority;

    /**
     * 触发类型（NULL 继承工作流）: 1-CRON 2-固定频率 3-固定延迟
     */
    private Integer triggerType;

    /**
     * 触发值（CRON表达式或秒数）
     */
    private String triggerValue;

    private String description;

    /**
     * 画布中的 X 坐标
     */
    private Double positionX;

    /**
     * 画布中的 Y 坐标
     */
    private Double positionY;

    @TableLogic
    private Integer isDeleted;

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

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }
}
