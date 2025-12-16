package com.sunny.job.worker.domain.entity;

/**
 * 工作流执行实例实体
 * <p>
 * Worker 创建/更新工作流执行实例时使用
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class JobWorkflowRun {

    private Long id;
    private Long namespaceId;
    private Long workflowId;
    private Integer triggerType;
    private Long triggerTime;

    /**
     * 下一次触发时间（毫秒，任务开始执行时预计算）
     */
    private Long nextTriggerTime;

    private Integer status;

    /**
     * 操作类型：ONLINE/TRIGGER/RERUN 等
     */
    private String op;

    /**
     * 逻辑删除: 0-正常 1-删除
     */
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

    public Integer getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(Integer triggerType) {
        this.triggerType = triggerType;
    }

    public Long getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(Long triggerTime) {
        this.triggerTime = triggerTime;
    }

    public Long getNextTriggerTime() {
        return nextTriggerTime;
    }

    public void setNextTriggerTime(Long nextTriggerTime) {
        this.nextTriggerTime = nextTriggerTime;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getOp() {
        return op;
    }

    public void setOp(String op) {
        this.op = op;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }
}
