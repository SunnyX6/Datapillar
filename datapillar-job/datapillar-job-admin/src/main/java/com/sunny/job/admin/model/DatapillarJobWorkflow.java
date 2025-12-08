package com.sunny.job.admin.model;

import java.util.Date;

/**
 * datapillar-job workflow instance
 *
 * @author datapillar-job-admin
 * @date 2025-11-06
 */
public class DatapillarJobWorkflow {

    private Long workflowId;
    private Long projectId;      // 项目ID
    private Long folderId;       // 所属文件夹ID
    private String name;         // 工作流名称
    private String description;  // 工作流描述
    private Integer version;     // 当前版本号
    private Long createdBy;      // 创建者ID
    private String status;       // DRAFT/RUNNING/COMPLETED/FAILED/CANCELLED
    private String workflowData; // 工作流画布数据(nodes和edges的JSON)
    private Date startTime;
    private Date endTime;
    private Date updateTime;     // 修改时间
    private Date createTime;

    // 关联查询字段（不存储在数据库，仅用于查询结果）
    private String folderName;   // 文件夹名称（从 job_workflow_folder 表关联查询）
    private String createdByName; // 创建者用户名（从 users 表关联查询）

    public Long getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(Long workflowId) {
        this.workflowId = workflowId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getFolderId() {
        return folderId;
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public String getWorkflowData() {
        return workflowData;
    }

    public void setWorkflowData(String workflowData) {
        this.workflowData = workflowData;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    @Override
    public String toString() {
        return "DatapillarJobWorkflow{" +
                "workflowId=" + workflowId +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", createdBy=" + createdBy +
                ", status='" + status + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", updateTime=" + updateTime +
                ", createTime=" + createTime +
                '}';
    }
}