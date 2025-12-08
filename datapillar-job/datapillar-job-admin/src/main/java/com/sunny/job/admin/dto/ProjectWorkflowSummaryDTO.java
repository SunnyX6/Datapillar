package com.sunny.job.admin.dto;

/**
 * 项目工作流统计DTO
 * 用于任务管理页面展示每个项目的工作流统计数据
 *
 * @author sunny
 * @date 2025-11-11
 */
public class ProjectWorkflowSummaryDTO {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 工作流总数
     */
    private Integer totalWorkflows;

    /**
     * 今日新增工作流数
     */
    private Integer todayNew;

    /**
     * 今日成功任务数
     */
    private Integer todaySuccess;

    /**
     * 今日失败任务数
     */
    private Integer todayFailed;

    /**
     * 运行中任务数
     */
    private Integer running;

    /**
     * 成功率（百分比）
     */
    private Integer successRate;

    /**
     * 项目描述
     */
    private String description;

    /**
     * 项目状态
     */
    private String status;

    /**
     * 项目标签
     */
    private String tags;

    // Getters and Setters

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public Integer getTotalWorkflows() {
        return totalWorkflows;
    }

    public void setTotalWorkflows(Integer totalWorkflows) {
        this.totalWorkflows = totalWorkflows;
    }

    public Integer getTodayNew() {
        return todayNew;
    }

    public void setTodayNew(Integer todayNew) {
        this.todayNew = todayNew;
    }

    public Integer getTodaySuccess() {
        return todaySuccess;
    }

    public void setTodaySuccess(Integer todaySuccess) {
        this.todaySuccess = todaySuccess;
    }

    public Integer getTodayFailed() {
        return todayFailed;
    }

    public void setTodayFailed(Integer todayFailed) {
        this.todayFailed = todayFailed;
    }

    public Integer getRunning() {
        return running;
    }

    public void setRunning(Integer running) {
        this.running = running;
    }

    public Integer getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(Integer successRate) {
        this.successRate = successRate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }
}
