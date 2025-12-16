package com.sunny.job.worker.domain.mapper;

import com.sunny.job.core.message.JobRunInfo;
import com.sunny.job.worker.domain.entity.JobRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 任务执行实例 Mapper
 * <p>
 * SQL 优化：
 * - 消除 JOIN：只查 job_run 表，job_info 从缓存获取
 * - 分页查询：所有查询方法都带 limit 参数
 * - 索引优化：使用 idx_bucket_status_trigger 组合索引
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobRunMapper {

    /**
     * 按 Bucket 查询待执行的任务（带分页）
     * <p>
     * 只返回 job_run 基础字段，job_info 从缓存补全
     *
     * @param bucketIds Bucket ID 集合
     * @param limit     最大返回条数
     * @return 待执行任务列表
     */
    List<JobRunInfo> selectWaitingJobsByBuckets(@Param("bucketIds") Collection<Integer> bucketIds,
                                                 @Param("limit") int limit);

    /**
     * 按单个 Bucket 查询待执行的任务（带分页）
     *
     * @param bucketId Bucket ID
     * @param limit    最大返回条数
     * @return 待执行任务列表
     */
    List<JobRunInfo> selectWaitingJobsByBucket(@Param("bucketId") Integer bucketId,
                                                @Param("limit") int limit);

    /**
     * 按 Bucket 查询新增的任务（带分页）
     *
     * @param lastMaxId 上次最大ID
     * @param bucketIds Bucket ID 集合
     * @param limit     最大返回条数
     * @return 新增任务列表
     */
    List<JobRunInfo> selectNewJobsByBuckets(@Param("lastMaxId") Long lastMaxId,
                                             @Param("bucketIds") Collection<Integer> bucketIds,
                                             @Param("limit") int limit);

    /**
     * 查询被重跑的任务
     * <p>
     * 检测内存中是 FAIL/TIMEOUT 但 DB 中变成 WAITING 的任务
     *
     * @param jobRunIds 需要检查的任务ID列表
     * @return 已被重跑（状态变为 WAITING）的任务列表
     */
    List<JobRunInfo> selectRerunJobs(@Param("jobRunIds") List<Long> jobRunIds);

    /**
     * 查询当前最大ID
     *
     * @return 最大ID，表为空时返回 null
     */
    Long selectMaxId();

    /**
     * 查询工作流下所有任务的状态
     *
     * @param workflowRunId 工作流执行实例ID
     * @return 状态列表
     */
    List<Integer> selectStatusByWorkflowRunId(@Param("workflowRunId") Long workflowRunId);

    /**
     * 更新任务状态
     *
     * @param jobRunId  任务执行实例ID
     * @param status    新状态
     * @param op        操作类型
     * @param workerId  执行者
     * @param startTime 开始时间（毫秒）
     * @param endTime   结束时间（毫秒）
     * @param message   结果消息
     * @return 影响行数
     */
    int updateStatus(@Param("jobRunId") Long jobRunId,
                     @Param("status") Integer status,
                     @Param("op") String op,
                     @Param("workerId") String workerId,
                     @Param("startTime") Long startTime,
                     @Param("endTime") Long endTime,
                     @Param("message") String message);

    /**
     * 更新任务为重试状态
     *
     * @param jobRunId   任务执行实例ID
     * @param op         操作类型
     * @param retryCount 新的重试次数
     * @return 影响行数
     */
    int updateForRetry(@Param("jobRunId") Long jobRunId,
                       @Param("op") String op,
                       @Param("retryCount") Integer retryCount);

    /**
     * 批量插入任务执行实例
     *
     * @param jobRuns 任务执行实例列表
     * @return 影响行数
     */
    int batchInsert(@Param("list") List<JobRun> jobRuns);

    /**
     * 根据 jobRunId 查询 bucketId
     *
     * @param jobRunId 任务执行实例ID
     * @return bucketId，不存在返回 null
     */
    Integer selectBucketIdById(@Param("jobRunId") Long jobRunId);

    /**
     * 根据 ID 查询单个任务
     *
     * @param jobRunId 任务执行实例ID
     * @return 任务信息，不存在返回 null
     */
    JobRunInfo selectById(@Param("jobRunId") Long jobRunId);

    /**
     * 按 workflowRunId 和 bucketIds 查询 job_run
     *
     * @param workflowRunId 工作流执行实例ID
     * @param bucketIds     Bucket ID 集合
     * @return job_run 列表
     */
    List<JobRunInfo> selectByWorkflowRunIdAndBuckets(@Param("workflowRunId") Long workflowRunId,
                                                      @Param("bucketIds") Collection<Integer> bucketIds);

    /**
     * 批量更新状态为 WAITING（重跑用）
     *
     * @param jobRunIds 任务ID列表
     * @param op        操作类型
     * @return 影响行数
     */
    int batchUpdateStatusToWaiting(@Param("jobRunIds") List<Long> jobRunIds, @Param("op") String op);

    /**
     * 批量更新状态为 CANCELLED（下线/取消用）
     *
     * @param jobRunIds 任务ID列表
     * @param op        操作类型
     * @return 影响行数
     */
    int batchUpdateStatusToCancelled(@Param("jobRunIds") List<Long> jobRunIds, @Param("op") String op);

    /**
     * 按时间窗口查询任务（预加载优化）
     * <p>
     * 查询指定 Bucket 在时间窗口内待触发的任务
     * 使用索引：idx_bucket_status_trigger (bucket_id, status, trigger_time)
     *
     * @param bucketIds   Bucket ID 集合
     * @param windowStart 窗口开始时间（毫秒）
     * @param windowEnd   窗口结束时间（毫秒）
     * @param status      任务状态
     * @param limit       最大返回条数
     * @return 任务列表
     */
    List<JobRunInfo> selectJobsInTimeWindow(@Param("bucketIds") Collection<Integer> bucketIds,
                                             @Param("windowStart") Long windowStart,
                                             @Param("windowEnd") Long windowEnd,
                                             @Param("status") Integer status,
                                             @Param("limit") int limit);
}
