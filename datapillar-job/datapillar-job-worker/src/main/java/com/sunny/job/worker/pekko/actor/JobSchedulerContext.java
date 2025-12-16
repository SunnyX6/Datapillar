package com.sunny.job.worker.pekko.actor;

import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.message.JobRunInfo;
import com.sunny.job.core.strategy.route.WorkerInfo;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * JobScheduler 上下文接口
 * <p>
 * 解耦 Scheduler 与 DB 操作，便于测试和扩展
 * <p>
 * 异步设计：
 * - 所有 DB 查询返回 CompletableFuture，避免阻塞 Actor 线程
 * - Actor 使用 pipeToSelf 模式处理异步结果
 * <p>
 * SQL 优化：
 * - 所有查询都按 Bucket 过滤，避免全表扫描
 * - job_info 从缓存获取，消除 JOIN
 * - 所有查询都带分页，防止 OOM
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public interface JobSchedulerContext {

    /**
     * 异步加载结果
     *
     * @param jobs     任务列表
     * @param newMaxId 新的最大 ID
     */
    record LoadResult(List<JobRunInfo> jobs, long newMaxId) {}

    /**
     * 按 Bucket 异步加载待执行的任务
     *
     * @param bucketIds Bucket ID 集合
     * @return 异步结果
     */
    CompletableFuture<LoadResult> loadWaitingJobsByBucketsAsync(Collection<Integer> bucketIds);

    /**
     * 按单个 Bucket 异步加载待执行的任务
     *
     * @param bucketId Bucket ID
     * @return 异步结果
     */
    CompletableFuture<LoadResult> loadWaitingJobsByBucketAsync(int bucketId);

    /**
     * 按 Bucket 异步加载新增的任务
     *
     * @param lastMaxId 上次最大 ID
     * @param bucketIds Bucket ID 集合
     * @return 异步结果
     */
    CompletableFuture<LoadResult> loadNewJobsByBucketsAsync(long lastMaxId, Collection<Integer> bucketIds);

    /**
     * 异步检测被重跑的任务
     * <p>
     * 检查内存中 FAIL/TIMEOUT 状态但 DB 中已变为 WAITING 的任务
     *
     * @param failedJobRunIds 内存中失败状态的任务 ID 列表
     * @return 异步结果（被重跑的任务列表）
     */
    CompletableFuture<LoadResult> detectRerunJobsAsync(List<Long> failedJobRunIds);

    /**
     * 获取可用的 Worker 列表
     * <p>
     * 用于路由策略选择目标 Worker
     *
     * @return Worker 信息列表
     */
    List<WorkerInfo> getAvailableWorkers();

    /**
     * 获取所有 Worker 状态（包括负载信息）
     * <p>
     * 用于调度器在本地容量不足时查找其他可用 Worker
     *
     * @return Worker 状态列表
     */
    List<WorkerCapacity> getAllWorkerCapacities();

    /**
     * Worker 容量信息
     */
    record WorkerCapacity(
            String address,
            int maxConcurrency,
            int currentRunning
    ) {
        public int availableCapacity() {
            return Math.max(0, maxConcurrency - currentRunning);
        }

        public boolean hasCapacity() {
            return maxConcurrency <= 0 || currentRunning < maxConcurrency;
        }
    }

    /**
     * 更新 Worker 信息
     * <p>
     * 当 Worker 上报心跳或负载信息时调用
     *
     * @param workerInfo Worker 信息
     */
    void updateWorkerInfo(WorkerInfo workerInfo);

    /**
     * 获取最大待调度任务数
     * <p>
     * 用于内存保护，防止 OOM
     *
     * @return 最大待调度任务数
     */
    int getMaxPendingTasks();

    /**
     * 异步批量写入 job_run 记录
     * <p>
     * 用于持久化新创建的任务
     *
     * @param jobs 任务列表
     * @return 异步结果（写入成功的任务 ID 列表）
     */
    CompletableFuture<List<Long>> persistJobRunsAsync(List<JobRunInfo> jobs);

    /**
     * 更新任务状态（同步）
     * <p>
     * 用于下线工作流时取消等待中的任务
     *
     * @param jobRunId 任务 ID
     * @param status   新状态
     * @param message  状态消息
     */
    void updateJobRunStatus(long jobRunId, JobStatus status, String message);

    /**
     * 从缓存补全 job_info 数据
     * <p>
     * 根据 jobId 从 Caffeine 缓存获取 job_info，补全 jobParams 等字段
     *
     * @param jobRun 任务运行信息（需要包含 jobId）
     */
    void enrichJobInfo(JobRunInfo jobRun);
}
