package com.sunny.job.worker.domain.entity;

/**
 * Bucket 租约持久化实体
 * <p>
 * 仅用于 Worker 重启时恢复 Bucket 所有权
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public class JobBucketLease {

    /**
     * Bucket ID (0 ~ 1023)
     */
    private Integer bucketId;

    /**
     * 持有者 Worker 地址
     */
    private String workerAddress;

    /**
     * 最后更新时间（毫秒）
     */
    private Long leaseTime;

    public JobBucketLease() {
    }

    public JobBucketLease(Integer bucketId, String workerAddress, Long leaseTime) {
        this.bucketId = bucketId;
        this.workerAddress = workerAddress;
        this.leaseTime = leaseTime;
    }

    public Integer getBucketId() {
        return bucketId;
    }

    public void setBucketId(Integer bucketId) {
        this.bucketId = bucketId;
    }

    public String getWorkerAddress() {
        return workerAddress;
    }

    public void setWorkerAddress(String workerAddress) {
        this.workerAddress = workerAddress;
    }

    public Long getLeaseTime() {
        return leaseTime;
    }

    public void setLeaseTime(Long leaseTime) {
        this.leaseTime = leaseTime;
    }
}
