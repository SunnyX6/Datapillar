package com.sunny.job.server.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;

/**
 * 任务定义实体
 * <p>
 * 任务必须属于某个工作流
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@TableName("job_info")
public class JobInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long namespaceId;

    private Long workflowId;

    private String jobName;

    /**
     * 状态: 0-禁用 1-启用
     */
    private Integer jobStatus;

    /**
     * 任务类型: 1-Shell 2-Python 3-Spark 4-Flink 5-HiveSQL 6-DataX 7-HTTP
     */
    private Integer jobType;

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

    public Integer getJobStatus() {
        return jobStatus;
    }

    public void setJobStatus(Integer jobStatus) {
        this.jobStatus = jobStatus;
    }

    public Integer getJobType() {
        return jobType;
    }

    public void setJobType(Integer jobType) {
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
     * 是否已启用
     */
    public boolean isEnabled() {
        return jobStatus != null && jobStatus == 1;
    }
}
