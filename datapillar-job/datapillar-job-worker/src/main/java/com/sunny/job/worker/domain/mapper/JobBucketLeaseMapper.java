package com.sunny.job.worker.domain.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Bucket 租约持久化 Mapper
 * <p>
 * 仅用于 Worker 重启时恢复 Bucket 所有权
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
@Mapper
public interface JobBucketLeaseMapper {

    /**
     * 查询指定 Worker 持有的 Bucket 列表
     * <p>
     * Worker 启动时调用，用于恢复之前持有的 Bucket
     *
     * @param workerAddress Worker 地址
     * @return Bucket ID 列表
     */
    List<Integer> selectBucketsByWorker(@Param("workerAddress") String workerAddress);

    /**
     * 插入或更新租约
     * <p>
     * Worker 认领 Bucket 后异步调用
     *
     * @param bucketId      Bucket ID
     * @param workerAddress Worker 地址
     * @param leaseTime     租约时间（毫秒）
     * @return 影响行数
     */
    int upsert(@Param("bucketId") Integer bucketId,
               @Param("workerAddress") String workerAddress,
               @Param("leaseTime") Long leaseTime);

    /**
     * 删除指定 Bucket 的租约
     * <p>
     * Worker 释放 Bucket 时调用
     *
     * @param bucketId Bucket ID
     * @return 影响行数
     */
    int deleteByBucketId(@Param("bucketId") Integer bucketId);

    /**
     * 删除指定 Worker 的所有租约
     * <p>
     * Worker 下线时调用
     *
     * @param workerAddress Worker 地址
     * @return 影响行数
     */
    int deleteByWorker(@Param("workerAddress") String workerAddress);
}
