package com.sunny.job.worker.pekko.ddata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunny.job.core.enums.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 任务运行状态管理器
 * <p>
 * 使用本地缓存管理任务运行状态
 * 不使用 CRDT（避免序列化问题，减少网络开销）
 * <p>
 * 数据结构: Cache[Long, JobRunState]
 * - Key: jobRunId
 * - Value: JobRunState (包含 status, updateTime)
 * <p>
 * 设计说明：
 * - 任务状态已在 job_run 表持久化
 * - 本地缓存用于快速查询依赖关系
 * - 不需要跨 Worker 同步（每个 Worker 只负责自己 Bucket 的任务）
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public class JobRunLocalCache {

    private static final Logger log = LoggerFactory.getLogger(JobRunLocalCache.class);

    /**
     * 本地缓存：jobRunId → JobRunState
     * <p>
     * 使用 Caffeine Cache，自动过期清理
     */
    private final Cache<Long, JobRunState> localCache;

    /**
     * 状态变化监听器
     */
    private volatile BiConsumer<Long, JobStatus> stateChangeListener;

    public JobRunLocalCache(long maxSize, Duration expireAfterWrite) {
        // 初始化 Caffeine Cache
        this.localCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWrite)
                .recordStats()
                .build();

        log.info("JobRunLocalCache 初始化完成，缓存配置: maxSize={}, expireAfterWrite={}",
                maxSize, expireAfterWrite);
    }

    /**
     * 订阅任务状态变化
     *
     * @param listener 状态变化监听器 (jobRunId, newStatus)
     */
    public void subscribe(BiConsumer<Long, JobStatus> listener) {
        this.stateChangeListener = listener;
        log.info("已设置任务运行状态变化监听器");
    }

    /**
     * 更新任务状态
     *
     * @param jobRunId jobRunId
     * @param status   新状态
     */
    public void updateStatus(long jobRunId, JobStatus status) {
        JobRunState state = new JobRunState(jobRunId, status, System.currentTimeMillis());
        updateState(state);
    }

    /**
     * 更新任务状态
     */
    private void updateState(JobRunState state) {
        log.debug("更新任务状态: jobRunId={}, status={}",
                state.jobRunId(), state.status());

        // 更新本地缓存
        localCache.put(state.jobRunId(), state);

        // 通知监听器
        if (stateChangeListener != null) {
            stateChangeListener.accept(state.jobRunId(), state.status());
        }
    }

    /**
     * 批量更新任务状态
     *
     * @param updates jobRunId → status 映射
     */
    public void batchUpdateStatus(Map<Long, JobStatus> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();

        // 批量更新本地缓存
        for (Map.Entry<Long, JobStatus> entry : updates.entrySet()) {
            JobRunState state = new JobRunState(entry.getKey(), entry.getValue(), now);
            localCache.put(entry.getKey(), state);
        }

        log.debug("批量更新 {} 个任务状态", updates.size());
    }

    /**
     * 获取任务状态（从本地缓存读取）
     *
     * @param jobRunId jobRunId
     * @return 任务状态，不存在返回 null
     */
    public JobStatus getStatus(long jobRunId) {
        JobRunState state = localCache.getIfPresent(jobRunId);
        return state != null ? state.status() : null;
    }

    /**
     * 检查任务是否为指定状态
     *
     * @param jobRunId       jobRunId
     * @param expectedStatus 期望的状态
     * @return true 如果状态匹配
     */
    public boolean isStatus(long jobRunId, JobStatus expectedStatus) {
        JobStatus status = getStatus(jobRunId);
        return status == expectedStatus;
    }

    /**
     * 检查任务是否已成功
     *
     * @param jobRunId jobRunId
     * @return true 如果任务已成功
     */
    public boolean isSuccess(long jobRunId) {
        return isStatus(jobRunId, JobStatus.SUCCESS);
    }

    /**
     * 检查任务是否为终态
     *
     * @param jobRunId jobRunId
     * @return true 如果任务已结束
     */
    public boolean isTerminal(long jobRunId) {
        JobStatus status = getStatus(jobRunId);
        return status != null && status.isTerminal();
    }

    /**
     * 获取工作流中所有任务的状态
     * <p>
     * 注意：此方法需要调用方提供 jobRunId 列表
     *
     * @param jobRunIds 任务运行 ID 列表
     * @return 状态列表
     */
    public List<JobStatus> getStatusByJobRunIds(List<Long> jobRunIds) {
        List<JobStatus> statuses = new ArrayList<>();
        for (Long jobRunId : jobRunIds) {
            JobStatus status = getStatus(jobRunId);
            if (status != null) {
                statuses.add(status);
            }
        }
        return statuses;
    }

    /**
     * 检查所有父任务是否已成功
     *
     * @param parentJobRunIds 父任务 ID 列表
     * @return true 如果所有父任务都已成功
     */
    public boolean allParentsSuccess(List<Long> parentJobRunIds) {
        if (parentJobRunIds == null || parentJobRunIds.isEmpty()) {
            return true;
        }

        for (Long parentId : parentJobRunIds) {
            if (!isSuccess(parentId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查是否有父任务失败
     *
     * @param parentJobRunIds 父任务 ID 列表
     * @return true 如果有父任务失败
     */
    public boolean anyParentFailed(List<Long> parentJobRunIds) {
        if (parentJobRunIds == null || parentJobRunIds.isEmpty()) {
            return false;
        }

        for (Long parentId : parentJobRunIds) {
            JobStatus status = getStatus(parentId);
            if (status != null && (status == JobStatus.FAIL || status == JobStatus.TIMEOUT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取缓存大小（估算值）
     */
    public long cacheSize() {
        return localCache.estimatedSize();
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计字符串
     */
    public String getCacheStats() {
        return localCache.stats().toString();
    }

    /**
     * 任务运行状态
     */
    public record JobRunState(
            long jobRunId,
            JobStatus status,
            long updateTime
    ) {
    }
}
