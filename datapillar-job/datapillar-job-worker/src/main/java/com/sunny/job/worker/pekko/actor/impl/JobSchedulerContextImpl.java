package com.sunny.job.worker.pekko.actor.impl;

import com.sunny.job.core.enums.BlockStrategy;
import com.sunny.job.core.enums.RouteStrategy;
import com.sunny.job.core.message.JobRunInfo;
import com.sunny.job.core.strategy.route.WorkerInfo;
import com.sunny.job.worker.domain.entity.JobInfo;
import com.sunny.job.worker.domain.mapper.JobDependencyMapper;
import com.sunny.job.worker.domain.mapper.JobRunMapper;
import com.sunny.job.worker.pekko.actor.JobSchedulerContext;
import com.sunny.job.worker.pekko.ddata.WorkerStateManager;
import com.sunny.job.worker.service.JobInfoCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * JobScheduler 上下文实现
 * <p>
 * 异步设计：
 * - 所有 DB 查询使用虚拟线程池异步执行
 * - 返回 CompletableFuture，不阻塞 Actor 线程
 * <p>
 * SQL 优化：
 * - 消除 JOIN：只查 job_run 表，job_info 从缓存获取
 * - 分页查询：默认每次最多加载 5000 条
 * - 按需缓存：job_info 使用 LRU 缓存
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Component
public class JobSchedulerContextImpl implements JobSchedulerContext {

    private static final Logger log = LoggerFactory.getLogger(JobSchedulerContextImpl.class);

    /**
     * 默认分页大小
     */
    private static final int DEFAULT_LIMIT = 5000;

    /**
     * DB 查询线程池（虚拟线程）
     */
    private final Executor dbExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final JobRunMapper jobRunMapper;
    private final JobDependencyMapper dependencyMapper;
    private final WorkerStateManager workerStateManager;
    private final JobInfoCacheService jobInfoCache;

    @Value("${datapillar.job.worker.address:localhost:8081}")
    private String localWorkerAddress;

    @Value("${datapillar.job.worker.max-pending-tasks:10000}")
    private int maxPendingTasks;

    public JobSchedulerContextImpl(JobRunMapper jobRunMapper,
                                    JobDependencyMapper dependencyMapper,
                                    WorkerStateManager workerStateManager,
                                    JobInfoCacheService jobInfoCache) {
        this.jobRunMapper = jobRunMapper;
        this.dependencyMapper = dependencyMapper;
        this.workerStateManager = workerStateManager;
        this.jobInfoCache = jobInfoCache;
    }

    @Override
    public CompletableFuture<LoadResult> loadWaitingJobsByBucketsAsync(Collection<Integer> bucketIds) {
        if (bucketIds == null || bucketIds.isEmpty()) {
            log.warn("bucketIds 为空，跳过加载");
            return CompletableFuture.completedFuture(new LoadResult(List.of(), 0L));
        }

        return CompletableFuture.supplyAsync(() -> {
            log.info("按 Bucket 加载待执行任务，bucketIds={}", bucketIds);

            List<JobRunInfo> jobs = jobRunMapper.selectWaitingJobsByBuckets(bucketIds, DEFAULT_LIMIT);
            log.info("查询到 {} 个待执行任务", jobs.size());

            processJobs(jobs);

            // 获取当前数据库中的最大 ID
            Long maxId = jobRunMapper.selectMaxId();
            long newMaxId = maxId != null ? maxId : 0L;

            log.info("加载完成，newMaxId={}", newMaxId);
            return new LoadResult(jobs, newMaxId);
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<LoadResult> loadWaitingJobsByBucketAsync(int bucketId) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("按单个 Bucket 加载待执行任务，bucketId={}", bucketId);

            List<JobRunInfo> jobs = jobRunMapper.selectWaitingJobsByBucket(bucketId, DEFAULT_LIMIT);
            log.info("查询到 {} 个待执行任务", jobs.size());

            processJobs(jobs);

            // 获取新的最大 ID
            long newMaxId = 0L;
            for (JobRunInfo job : jobs) {
                if (job.getJobRunId() > newMaxId) {
                    newMaxId = job.getJobRunId();
                }
            }

            return new LoadResult(jobs, newMaxId);
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<LoadResult> loadNewJobsByBucketsAsync(long lastMaxId, Collection<Integer> bucketIds) {
        if (bucketIds == null || bucketIds.isEmpty()) {
            return CompletableFuture.completedFuture(new LoadResult(List.of(), lastMaxId));
        }

        return CompletableFuture.supplyAsync(() -> {
            List<JobRunInfo> jobs = jobRunMapper.selectNewJobsByBuckets(lastMaxId, bucketIds, DEFAULT_LIMIT);

            if (jobs.isEmpty()) {
                return new LoadResult(List.of(), lastMaxId);
            }

            log.debug("发现 {} 个新任务（按 Bucket 过滤）", jobs.size());

            processJobs(jobs);

            // 计算新的最大 ID
            long newMaxId = lastMaxId;
            for (JobRunInfo job : jobs) {
                if (job.getJobRunId() > newMaxId) {
                    newMaxId = job.getJobRunId();
                }
            }

            return new LoadResult(jobs, newMaxId);
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<LoadResult> detectRerunJobsAsync(List<Long> failedJobRunIds) {
        if (failedJobRunIds == null || failedJobRunIds.isEmpty()) {
            return CompletableFuture.completedFuture(new LoadResult(List.of(), 0L));
        }

        return CompletableFuture.supplyAsync(() -> {
            // 查询这些任务中已变为 WAITING 的（被重跑的）
            List<JobRunInfo> rerunJobs = jobRunMapper.selectRerunJobs(failedJobRunIds);

            if (rerunJobs.isEmpty()) {
                return new LoadResult(List.of(), 0L);
            }

            log.info("检测到 {} 个被重跑的任务", rerunJobs.size());

            processJobs(rerunJobs);

            return new LoadResult(rerunJobs, 0L);
        }, dbExecutor);
    }

    @Override
    public List<WorkerInfo> getAvailableWorkers() {
        // 从 CRDT 获取存活的 Worker
        List<WorkerInfo> aliveWorkers = workerStateManager.getAliveWorkers();

        // 如果没有存活的 Worker，返回本地 Worker
        if (aliveWorkers.isEmpty()) {
            log.warn("没有存活的 Worker，使用本地 Worker: {}", localWorkerAddress);
            return List.of(WorkerInfo.of(localWorkerAddress));
        }

        return aliveWorkers;
    }

    @Override
    public List<WorkerCapacity> getAllWorkerCapacities() {
        List<WorkerStateManager.WorkerState> states = workerStateManager.getAllWorkerStates();
        return states.stream()
                .map(s -> new WorkerCapacity(s.address(), s.maxConcurrency(), s.currentRunning()))
                .toList();
    }

    @Override
    public void updateWorkerInfo(WorkerInfo workerInfo) {
        log.debug("更新 Worker 信息: {}", workerInfo.address());
        workerStateManager.updateWorkerState(
                workerInfo.address(),
                workerInfo.maxConcurrency(),
                workerInfo.currentRunning()
        );
    }

    @Override
    public int getMaxPendingTasks() {
        return maxPendingTasks;
    }

    /**
     * 处理任务列表：从缓存补全 job_info、加载依赖关系
     */
    private void processJobs(List<JobRunInfo> jobs) {
        for (JobRunInfo job : jobs) {
            // 从缓存补全 job_info 数据
            enrichJobInfo(job);

            // 加载依赖关系
            List<Long> parentIds = dependencyMapper.selectParentRunIds(job.getJobRunId());
            job.setParentJobRunIds(parentIds);
        }
    }

    /**
     * 从缓存补全 job_info 数据
     * <p>
     * SQL 查询只返回 job_run 表字段，job_info 数据从 LRU 缓存获取
     */
    private void enrichJobInfo(JobRunInfo jobRun) {
        JobInfo jobInfo = jobInfoCache.get(jobRun.getJobId());
        if (jobInfo == null) {
            log.warn("任务定义不存在: jobId={}", jobRun.getJobId());
            return;
        }

        jobRun.setJobName(jobInfo.getJobName());
        jobRun.setJobType(jobInfo.getJobType());
        jobRun.setJobParams(jobInfo.getJobParams());
        jobRun.setRouteStrategy(RouteStrategy.of(jobInfo.getRouteStrategy()));
        jobRun.setBlockStrategy(BlockStrategy.of(jobInfo.getBlockStrategy()));
        jobRun.setTimeoutSeconds(jobInfo.getTimeoutSeconds());
        jobRun.setMaxRetryTimes(jobInfo.getMaxRetryTimes());
        jobRun.setRetryInterval(jobInfo.getRetryInterval());
    }
}
