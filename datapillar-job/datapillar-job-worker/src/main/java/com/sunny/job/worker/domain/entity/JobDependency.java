package com.sunny.job.worker.domain.entity;

/**
 * 任务依赖关系实体（设计阶段）
 * <p>
 * Worker 查询任务依赖关系时使用
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class JobDependency {

    private Long jobId;
    private Long parentJobId;

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Long getParentJobId() {
        return parentJobId;
    }

    public void setParentJobId(Long parentJobId) {
        this.parentJobId = parentJobId;
    }
}
