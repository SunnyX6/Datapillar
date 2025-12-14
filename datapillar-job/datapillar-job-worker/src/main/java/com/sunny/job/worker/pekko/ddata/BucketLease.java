package com.sunny.job.worker.pekko.ddata;

import java.io.Serializable;

/**
 * Bucket 租约
 * <p>
 * 记录 Bucket 的所有权信息，用于 CRDT 同步
 *
 * @param bucketId      Bucket ID (0 ~ BUCKET_COUNT-1)
 * @param workerAddress 当前 Owner 的地址
 * @param leaseTime     最后续租时间（毫秒时间戳）
 * @author SunnyX6
 * @date 2025-12-14
 */
public record BucketLease(
        int bucketId,
        String workerAddress,
        long leaseTime
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 默认 Bucket 数量
     */
    public static final int DEFAULT_BUCKET_COUNT = 1024;

    /**
     * 租约超时时间（毫秒）
     * <p>
     * 超过此时间未续租，Bucket 可被其他 Worker 抢占
     */
    public static final long LEASE_TIMEOUT_MS = 30_000;

    /**
     * 续租间隔（毫秒）
     */
    public static final long RENEWAL_INTERVAL_MS = 10_000;

    /**
     * 创建新租约
     *
     * @param bucketId      Bucket ID
     * @param workerAddress Worker 地址
     * @return 新租约
     */
    public static BucketLease create(int bucketId, String workerAddress) {
        return new BucketLease(bucketId, workerAddress, System.currentTimeMillis());
    }

    /**
     * 续租（更新时间戳）
     *
     * @return 续租后的租约
     */
    public BucketLease renew() {
        return new BucketLease(bucketId, workerAddress, System.currentTimeMillis());
    }

    /**
     * 检查租约是否过期
     *
     * @return true 如果已过期
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - leaseTime > LEASE_TIMEOUT_MS;
    }

    /**
     * 检查是否属于指定 Worker
     *
     * @param address Worker 地址
     * @return true 如果属于该 Worker
     */
    public boolean isOwnedBy(String address) {
        return workerAddress != null && workerAddress.equals(address);
    }

    /**
     * 计算 job_id 对应的 bucket_id
     *
     * @param jobId       任务 ID
     * @param bucketCount Bucket 总数
     * @return bucket_id
     */
    public static int calculateBucketId(long jobId, int bucketCount) {
        return (int) (Math.abs(jobId) % bucketCount);
    }

    /**
     * 计算 job_id 对应的 bucket_id（使用默认 Bucket 数量）
     *
     * @param jobId 任务 ID
     * @return bucket_id
     */
    public static int calculateBucketId(long jobId) {
        return calculateBucketId(jobId, DEFAULT_BUCKET_COUNT);
    }
}
