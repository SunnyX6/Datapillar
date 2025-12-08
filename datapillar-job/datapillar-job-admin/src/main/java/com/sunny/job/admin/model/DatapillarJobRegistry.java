package com.sunny.job.admin.model;

import java.util.Date;

/**
 * @author Sunny
 * @version 1.0.0
 * @since 2025-12-08
 */
public class DatapillarJobRegistry {

    private int id;
    private String registryGroup;
    private String registryKey;
    private String registryValue;
    private Date updateTime;

    // 负载指标字段
    private Double cpuUsage;          // CPU使用率(0-100)
    private Double memoryUsage;       // 内存使用率(0-100)
    private Integer runningTasks;     // 运行中的任务数
    private Double loadScore;         // 负载评分(综合指标)

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getRegistryGroup() {
        return registryGroup;
    }

    public void setRegistryGroup(String registryGroup) {
        this.registryGroup = registryGroup;
    }

    public String getRegistryKey() {
        return registryKey;
    }

    public void setRegistryKey(String registryKey) {
        this.registryKey = registryKey;
    }

    public String getRegistryValue() {
        return registryValue;
    }

    public void setRegistryValue(String registryValue) {
        this.registryValue = registryValue;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(Double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public Double getMemoryUsage() {
        return memoryUsage;
    }

    public void setMemoryUsage(Double memoryUsage) {
        this.memoryUsage = memoryUsage;
    }

    public Integer getRunningTasks() {
        return runningTasks;
    }

    public void setRunningTasks(Integer runningTasks) {
        this.runningTasks = runningTasks;
    }

    public Double getLoadScore() {
        return loadScore;
    }

    public void setLoadScore(Double loadScore) {
        this.loadScore = loadScore;
    }
}
