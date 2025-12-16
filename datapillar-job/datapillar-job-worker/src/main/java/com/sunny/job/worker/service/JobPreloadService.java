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
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务预加载服务
 * <p>
 * 核心设计：
 * - Bucket 获得时：加载该 Bucket 的所有 WAITING 任务到内存
 * - 事件驱动：通过 CRDT 广播 maxJobRunId 变化，触发增量加载
 * - 无轮询：完全事件驱动，不依赖定时轮询 DB
 * <p>
 * 线程安全：使用 ConcurrentHashMap 存储缓存
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
@Service
public class JobPreloadService {

    private static final Logger log = LoggerFactory.getLogger(JobPreloadService.class);

    /**
     * 预加载缓存：bucketId → Set<JobRunInfo>
     */
    private final Map<Integer, Set<JobRunInfo>> preloadCache = new ConcurrentHashMap<>();

    /**
     * 已加载的任务 ID 集合（用于去重）
     */
    private final Set<Long> loadedJobRunIds = ConcurrentHashMap.newKeySet();

    /**
     * 当前持有的 Bucket 集合
     */
    private final Set<Integer> myBuckets = ConcurrentHashMap.newKeySet();

    /**
     * 本地已知的最大 jobRunId（用于增量加载）
     */
    private final AtomicLong localMaxId = new AtomicLong(0);

    private final JobRunMapper jobRunMapper;
    private final JobDependencyMapper dependencyMapper;
    private final JobInfoCacheService jobInfoCache;
    private final JobComponentCacheService componentCache;

    /**
     * 每次加载的最大任务数
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
                              JobInfoCacheService jobInfoCache,
                              JobComponentCacheService componentCache) {
        this.jobRunMapper = jobRunMapper;
        this.dependencyMapper = dependencyMapper;
        this.jobInfoCache = jobInfoCache;
        this.componentCache = componentCache;
    }

    /**
     * Bucket 获得时加载该 Bucket 的所有 WAITING 任务
     *
     * @param bucketId Bucket ID
     */
    public void onBucketAcquired(int bucketId) {
        myBuckets.add(bucketId);

        try {
            List<JobRunInfo> jobs = jobRunMapper.selectWaitingJobsByBucket(bucketId, preloadBatchSize);
            if (!jobs.isEmpty()) {
                addJobsToCache(jobs);
                log.info("Bucket {} 获得，加载 {} 个任务", bucketId, jobs.size());

                // 更新 localMaxId
                for (JobRunInfo job : jobs) {
                    long jobRunId = job.getJobRunId();
                    if (jobRunId > localMaxId.get()) {
                        localMaxId.set(jobRunId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("加载 Bucket {} 任务失败", bucketId, e);
        }
    }

    /**
     * Bucket 丢失时清理缓存
     *
     * @param bucketId Bucket ID
     */
    public void onBucketLost(int bucketId) {
        myBuckets.remove(bucketId);
        Set<JobRunInfo> removed = preloadCache.remove(bucketId);
        if (removed != null) {
            for (JobRunInfo job : removed) {
                loadedJobRunIds.remove(job.getJobRunId());
            }
            log.info("Bucket {} 丢失，清理 {} 个任务缓存", bucketId, removed.size());
        }
    }

    /**
     * 将任务添加到缓存
     */
    private void addJobsToCache(List<JobRunInfo> jobs) {
        // 筛选需要加载的任务（去重 + 内存保护）
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

        // 批量查询依赖关系
        Map<Long, List<Long>> dependencyMap = new HashMap<>();
        if (!newJobRunIds.isEmpty()) {
            List<JobRunDependency> dependencies = dependencyMapper.selectParentRunIdsBatch(newJobRunIds);
            for (JobRunDependency dep : dependencies) {
                dependencyMap.computeIfAbsent(dep.getJobRunId(), k -> new ArrayList<>())
                        .add(dep.getParentRunId());
            }
        }

        // 组装数据并加入缓存
        for (JobRunInfo job : newJobs) {
            enrichJobInfo(job);
            List<Long> parentIds = dependencyMap.getOrDefault(job.getJobRunId(), List.of());
            job.setParentJobRunIds(parentIds);

            preloadCache.computeIfAbsent(job.getBucketId(), k -> ConcurrentHashMap.newKeySet())
                    .add(job);
            loadedJobRunIds.add(job.getJobRunId());
        }
    }

    /**
     * 获取指定 Bucket 的到期任务
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

        for (JobRunInfo job : expired) {
            cached.remove(job);
            loadedJobRunIds.remove(job.getJobRunId());
        }

        return expired;
    }

    /**
     * 获取指定 Bucket 集合的所有到期任务
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
     * 更新持有的 Bucket 集合（兼容旧接口）
     */
    public void updateBucket(int bucketId, boolean acquired) {
        if (acquired) {
            onBucketAcquired(bucketId);
        } else {
            onBucketLost(bucketId);
        }
    }

    /**
     * 设置持有的 Bucket 集合
     */
    public void setBuckets(Set<Integer> buckets) {
        myBuckets.clear();
        myBuckets.addAll(buckets);
    }

    /**
     * 从缓存移除指定任务
     */
    public void removeFromCache(long jobRunId, int bucketId) {
        Set<JobRunInfo> cached = preloadCache.get(bucketId);
        if (cached != null) {
            cached.removeIf(job -> job.getJobRunId() == jobRunId);
        }
        loadedJobRunIds.remove(jobRunId);
    }

    /**
     * 检查任务是否在缓存中
     */
    public boolean isInCache(long jobRunId) {
        return loadedJobRunIds.contains(jobRunId);
    }

    /**
     * 获取缓存中的任务总数
     */
    public int getCacheSize() {
        return loadedJobRunIds.size();
    }

    /**
     * 获取本地已知的最大 jobRunId
     */
    public long getLocalMaxId() {
        return localMaxId.get();
    }

    /**
     * 定时清理过期的任务 ID（内存保护）
     */
    @Scheduled(fixedRate = 30000, initialDelay = 30000)
    public void cleanupStaleIds() {
        if (loadedJobRunIds.isEmpty()) {
            return;
        }

        Set<Long> validIds = ConcurrentHashMap.newKeySet();
        for (Set<JobRunInfo> jobs : preloadCache.values()) {
            for (JobRunInfo job : jobs) {
                validIds.add(job.getJobRunId());
            }
        }

        int beforeSize = loadedJobRunIds.size();
        loadedJobRunIds.retainAll(validIds);
        int cleaned = beforeSize - loadedJobRunIds.size();

        if (cleaned > 0) {
            log.info("清理 {} 个过期的任务 ID，当前缓存大小: {}", cleaned, loadedJobRunIds.size());
        }
    }

    private boolean canAddNewId() {
        return loadedJobRunIds.size() < maxCachedIds;
    }

    private void enrichJobInfo(JobRunInfo jobRun) {
        JobInfo jobInfo = jobInfoCache.get(jobRun.getJobId());
        if (jobInfo == null) {
            log.warn("任务定义不存在: jobId={}", jobRun.getJobId());
            return;
        }

        jobRun.setJobName(jobInfo.getJobName());
        jobRun.setJobType(componentCache.getComponentCode(jobInfo.getJobType()));
        jobRun.setJobParams(jobInfo.getJobParams());
        jobRun.setRouteStrategy(RouteStrategy.of(jobInfo.getRouteStrategy()));
        jobRun.setBlockStrategy(BlockStrategy.of(jobInfo.getBlockStrategy()));
        jobRun.setTimeoutSeconds(jobInfo.getTimeoutSeconds());
        jobRun.setMaxRetryTimes(jobInfo.getMaxRetryTimes());
        jobRun.setRetryInterval(jobInfo.getRetryInterval());
    }
}
