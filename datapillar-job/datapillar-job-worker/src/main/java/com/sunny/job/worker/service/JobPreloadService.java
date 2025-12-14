package com.sunny.job.worker.service;

import com.sunny.job.core.enums.BlockStrategy;
import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.enums.RouteStrategy;
import com.sunny.job.core.message.JobRunInfo;
import com.sunny.job.worker.domain.entity.JobInfo;
import com.sunny.job.worker.domain.entity.JobRunDependency;
import com.sunny.job.worker.domain.mapper.JobDependencyMapper;
import com.sunny.job.worker.domain.mapper.JobRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务预加载服务
 * <p>
 * 核心优化：
 * - 定时批量从 DB 预加载未来 N 秒内要触发的任务
 * - Actor 从内存缓存读取，避免每次定时触发都查 DB
 * - 减少 DB 查询频率，提升调度吞吐量
 * <p>
 * 设计要点：
 * - 预加载窗口：默认 5 秒（可配置）
 * - 预加载频率：每秒一次
 * - 线程安全：使用 ConcurrentHashMap 存储缓存
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
@Service
public class JobPreloadService {

    private static final Logger log = LoggerFactory.getLogger(JobPreloadService.class);

    /**
     * 预加载缓存：bucketId → Set<JobRunInfo>
     * <p>
     * 按 Bucket 分组存储，方便 Scheduler 按 Bucket 获取
     */
    private final Map<Integer, Set<JobRunInfo>> preloadCache = new ConcurrentHashMap<>();

    /**
     * 已加载的任务 ID 集合（用于去重）
     */
    private final Set<Long> loadedJobRunIds = ConcurrentHashMap.newKeySet();

    /**
     * 当前持有的 Bucket 集合（由 BucketStateManager 更新）
     * <p>
     * 使用 ConcurrentHashMap.newKeySet() 替代 CopyOnWriteArraySet
     * 避免写操作时的数组复制开销
     */
    private final Set<Integer> myBuckets = ConcurrentHashMap.newKeySet();

    private final JobRunMapper jobRunMapper;
    private final JobDependencyMapper dependencyMapper;
    private final JobInfoCacheService jobInfoCache;

    /**
     * 预加载时间窗口（毫秒）
     */
    @Value("${datapillar.job.worker.preload-window-ms:5000}")
    private long preloadWindowMs;

    /**
     * 每次预加载的最大任务数
     */
    @Value("${datapillar.job.worker.preload-batch-size:1000}")
    private int preloadBatchSize;

    /**
     * loadedJobRunIds 最大容量（内存保护）
     */
    @Value("${datapillar.job.worker.preload-max-cached-ids:50000}")
    private int maxCachedIds;

    public JobPreloadService(JobRunMapper jobRunMapper,
                              JobDependencyMapper dependencyMapper,
                              JobInfoCacheService jobInfoCache) {
        this.jobRunMapper = jobRunMapper;
        this.dependencyMapper = dependencyMapper;
        this.jobInfoCache = jobInfoCache;
    }

    /**
     * 定时预加载任务
     * <p>
     * 每秒执行一次，加载未来 preloadWindowMs 内要触发的任务
     */
    @Scheduled(fixedRate = 1000)
    public void preloadJobs() {
        if (myBuckets.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long windowEnd = now + preloadWindowMs;

        try {
            // 查询未来窗口内要触发的任务
            List<JobRunInfo> jobs = jobRunMapper.selectJobsInTimeWindow(
                    myBuckets, now, windowEnd, JobStatus.WAITING.getCode(), preloadBatchSize);

            if (jobs.isEmpty()) {
                return;
            }

            // 1. 筛选需要加载的任务（去重 + 内存保护）
            List<JobRunInfo> newJobs = new ArrayList<>();
            List<Long> newJobRunIds = new ArrayList<>();
            for (JobRunInfo job : jobs) {
                if (loadedJobRunIds.contains(job.getJobRunId())) {
                    continue;
                }
                if (!canAddNewId()) {
                    log.warn("预加载缓存已达上限 {}，跳过剩余任务", maxCachedIds);
                    break;
                }
                newJobs.add(job);
                newJobRunIds.add(job.getJobRunId());
            }

            if (newJobs.isEmpty()) {
                return;
            }

            // 2. 批量查询依赖关系（优化 N+1 问题）
            Map<Long, List<Long>> dependencyMap = new HashMap<>();
            if (!newJobRunIds.isEmpty()) {
                List<JobRunDependency> dependencies = dependencyMapper.selectParentRunIdsBatch(newJobRunIds);
                for (JobRunDependency dep : dependencies) {
                    dependencyMap.computeIfAbsent(dep.getJobRunId(), k -> new ArrayList<>())
                            .add(dep.getParentRunId());
                }
            }

            // 3. 组装数据并加入缓存
            for (JobRunInfo job : newJobs) {
                // 补全 job_info 数据
                enrichJobInfo(job);

                // 设置依赖关系
                List<Long> parentIds = dependencyMap.getOrDefault(job.getJobRunId(), List.of());
                job.setParentJobRunIds(parentIds);

                // 加入缓存
                preloadCache.computeIfAbsent(job.getBucketId(), k -> ConcurrentHashMap.newKeySet())
                        .add(job);
                loadedJobRunIds.add(job.getJobRunId());
            }

            if (!newJobs.isEmpty()) {
                log.debug("预加载 {} 个任务，窗口=[{}, {}]", newJobs.size(), now, windowEnd);
            }
        } catch (Exception e) {
            log.error("预加载任务失败", e);
        }
    }

    /**
     * 获取指定 Bucket 的预加载任务（已到期的）
     *
     * @param bucketId Bucket ID
     * @param now      当前时间
     * @return 到期的任务列表
     */
    public List<JobRunInfo> pollExpiredJobs(int bucketId, long now) {
        Set<JobRunInfo> cached = preloadCache.get(bucketId);
        if (cached == null || cached.isEmpty()) {
            return List.of();
        }

        List<JobRunInfo> expired = new ArrayList<>();
        for (JobRunInfo job : cached) {
            if (job.getTriggerTime() <= now && job.getStatus() == JobStatus.WAITING) {
                expired.add(job);
            }
        }

        // 从缓存移除已取出的任务
        for (JobRunInfo job : expired) {
            cached.remove(job);
            loadedJobRunIds.remove(job.getJobRunId());
        }

        return expired;
    }

    /**
     * 获取指定 Bucket 集合的所有预加载任务（已到期的）
     *
     * @param bucketIds Bucket ID 集合
     * @param now       当前时间
     * @return 到期的任务列表
     */
    public List<JobRunInfo> pollExpiredJobs(Collection<Integer> bucketIds, long now) {
        List<JobRunInfo> allExpired = new ArrayList<>();
        for (Integer bucketId : bucketIds) {
            allExpired.addAll(pollExpiredJobs(bucketId, now));
        }
        return allExpired;
    }

    /**
     * 更新持有的 Bucket 集合
     *
     * @param bucketId Bucket ID
     * @param acquired true=获得，false=丢失
     */
    public void updateBucket(int bucketId, boolean acquired) {
        if (acquired) {
            myBuckets.add(bucketId);
        } else {
            myBuckets.remove(bucketId);
            // 清理该 Bucket 的缓存
            Set<JobRunInfo> removed = preloadCache.remove(bucketId);
            if (removed != null) {
                for (JobRunInfo job : removed) {
                    loadedJobRunIds.remove(job.getJobRunId());
                }
            }
        }
    }

    /**
     * 设置持有的 Bucket 集合
     *
     * @param buckets Bucket 集合
     */
    public void setBuckets(Set<Integer> buckets) {
        myBuckets.clear();
        myBuckets.addAll(buckets);
    }

    /**
     * 从缓存移除指定任务（任务状态变更时调用）
     *
     * @param jobRunId 任务 ID
     * @param bucketId Bucket ID
     */
    public void removeFromCache(long jobRunId, int bucketId) {
        Set<JobRunInfo> cached = preloadCache.get(bucketId);
        if (cached != null) {
            cached.removeIf(job -> job.getJobRunId() == jobRunId);
        }
        loadedJobRunIds.remove(jobRunId);
    }

    /**
     * 检查任务是否在预加载缓存中
     *
     * @param jobRunId 任务 ID
     * @return true 如果在缓存中
     */
    public boolean isInCache(long jobRunId) {
        return loadedJobRunIds.contains(jobRunId);
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存中的任务总数
     */
    public int getCacheSize() {
        return loadedJobRunIds.size();
    }

    /**
     * 定时清理过期的任务 ID
     * <p>
     * 每 30 秒执行一次，清理 loadedJobRunIds 中不在 preloadCache 的条目
     * 防止内存泄漏
     */
    @Scheduled(fixedRate = 30000, initialDelay = 30000)
    public void cleanupStaleIds() {
        if (loadedJobRunIds.isEmpty()) {
            return;
        }

        // 收集所有在 preloadCache 中的任务 ID
        Set<Long> validIds = ConcurrentHashMap.newKeySet();
        for (Set<JobRunInfo> jobs : preloadCache.values()) {
            for (JobRunInfo job : jobs) {
                validIds.add(job.getJobRunId());
            }
        }

        // 清理不在 preloadCache 中的任务 ID
        int beforeSize = loadedJobRunIds.size();
        loadedJobRunIds.retainAll(validIds);
        int cleaned = beforeSize - loadedJobRunIds.size();

        if (cleaned > 0) {
            log.info("清理 {} 个过期的任务 ID，当前缓存大小: {}", cleaned, loadedJobRunIds.size());
        }
    }

    /**
     * 检查是否可以添加新任务 ID（内存保护）
     *
     * @return true 如果可以添加
     */
    private boolean canAddNewId() {
        return loadedJobRunIds.size() < maxCachedIds;
    }

    /**
     * 从缓存补全 job_info 数据
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
