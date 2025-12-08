package com.sunny.job.admin.model;

import java.util.Date;

/**
 * datapillar-job dependency
 *
 * @author datapillar-job-admin
 * @date 2025-11-06
 */
public class DatapillarJobDependency {

    private Long id;
    private Long workflowId;      // workflow ID
    private Integer fromJobId;    // 被依赖任务ID（上游）
    private Integer toJobId;      // 依赖任务ID（下游）
    private String dependencyType; // SUCCESS/FAILURE/COMPLETE
    private Date createTime;
    private Date updateTime;

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

    public Integer getFromJobId() {
        return fromJobId;
    }

    public void setFromJobId(Integer fromJobId) {
        this.fromJobId = fromJobId;
    }

    public Integer getToJobId() {
        return toJobId;
    }

    public void setToJobId(Integer toJobId) {
        this.toJobId = toJobId;
    }

    public String getDependencyType() {
        return dependencyType;
    }

    public void setDependencyType(String dependencyType) {
        this.dependencyType = dependencyType;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "DatapillarJobDependency{" +
                "id=" + id +
                ", workflowId=" + workflowId +
                ", fromJobId=" + fromJobId +
                ", toJobId=" + toJobId +
                ", dependencyType='" + dependencyType + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}