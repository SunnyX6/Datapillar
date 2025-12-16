package com.sunny.job.server.dto;

/**
 * 依赖关系 DTO
 *
 * @author SunnyX6
 * @date 2025-12-16
 */
public class Dependency {

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
