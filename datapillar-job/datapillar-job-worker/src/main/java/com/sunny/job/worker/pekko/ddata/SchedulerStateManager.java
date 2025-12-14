package com.sunny.job.worker.pekko.ddata;

import com.sunny.job.core.enums.JobStatus;
import org.apache.pekko.actor.typed.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Scheduler 状态管理器（本地缓存）
 * <p>
 * 注意：在去中心化架构下，此组件主要用于本地状态缓存
 * 每个 Worker 有自己的本地 JobScheduler，不需要跨 Worker 状态同步
 * <p>
 * 当前实现：仅使用本地缓存，CRDT 同步功能待后续实现
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class SchedulerStateManager {

    private static final Logger log = LoggerFactory.getLogger(SchedulerStateManager.class);

    /**
     * CRDT 数据过期时间（24小时）
     */
    private static final long DATA_EXPIRE_MILLIS = 24 * 60 * 60 * 1000L;

    private final ActorSystem<?> system;

    /**
     * 本地缓存
     */
    private final Map<Long, JobRunState> localCache = new ConcurrentHashMap<>();

    /**
     * 记录的最大 jobRunId，用于增量加载
     */
    private volatile long lastMaxId = 0;

    /**
     * 状态变化监听器
     */
    private volatile BiConsumer<Long, JobRunState> stateChangeListener;

    public SchedulerStateManager(ActorSystem<?> system) {
        this.system = system;
        log.info("SchedulerStateManager 初始化完成（本地缓存模式）");
    }

    /**
     * 订阅状态变化
     *
     * @param listener 状态变化监听器 (jobRunId, newState)
     */
    public void subscribe(BiConsumer<Long, JobRunState> listener) {
        this.stateChangeListener = listener;
        log.info("已设置状态变化监听器");
    }

    /**
     * 更新 job_run 状态
     *
     * @param state job_run 状态
     */
    public void updateJobRunState(JobRunState state) {
        updateJobRunState(state, false);
    }

    /**
     * 更新 job_run 状态
     *
     * @param state    job_run 状态
     * @param majority 是否使用多数派写入（当前实现忽略此参数）
     */
    public void updateJobRunState(JobRunState state, boolean majority) {
        log.debug("更新 job_run 状态: jobRunId={}, status={}", state.getJobRunId(), state.getStatus());

        // 更新本地缓存
        JobRunState oldState = localCache.put(state.getJobRunId(), state);

        // 更新最大 ID
        if (state.getJobRunId() > lastMaxId) {
            lastMaxId = state.getJobRunId();
        }

        // 通知监听器
        if (stateChangeListener != null && (oldState == null || oldState.getStatus() != state.getStatus())) {
            stateChangeListener.accept(state.getJobRunId(), state);
        }
    }

    /**
     * 批量更新 job_run 状态
     *
     * @param states job_run 状态列表
     */
    public void batchUpdateJobRunStates(List<JobRunState> states) {
        if (states == null || states.isEmpty()) {
            return;
        }

        log.info("批量更新 {} 个 job_run 状态", states.size());

        for (JobRunState state : states) {
            localCache.put(state.getJobRunId(), state);
            if (state.getJobRunId() > lastMaxId) {
                lastMaxId = state.getJobRunId();
            }
        }
    }

    /**
     * 更新 job_run 状态
     *
     * @param jobRunId  任务执行实例 ID
     * @param newStatus 新状态
     */
    public void updateStatus(long jobRunId, JobStatus newStatus) {
        JobRunState existing = localCache.get(jobRunId);
        if (existing == null) {
            log.warn("更新状态失败，job_run 不存在于缓存: jobRunId={}", jobRunId);
            return;
        }

        JobRunState updated = existing.withStatus(newStatus.getCode());
        updateJobRunState(updated, true);
    }

    /**
     * 从缓存恢复状态
     *
     * @param consumer 状态消费者
     * @return 恢复的最大 jobRunId
     */
    public long recoverFromCrdt(Consumer<JobRunState> consumer) {
        log.info("从本地缓存恢复状态，共 {} 个", localCache.size());

        long maxId = 0;
        for (JobRunState state : localCache.values()) {
            consumer.accept(state);
            if (state.getJobRunId() > maxId) {
                maxId = state.getJobRunId();
            }
        }

        return maxId;
    }

    /**
     * 获取本地缓存中的 job_run 状态
     *
     * @param jobRunId 任务执行实例 ID
     * @return job_run 状态，不存在返回 null
     */
    public JobRunState getJobRunState(long jobRunId) {
        return localCache.get(jobRunId);
    }

    /**
     * 获取所有本地缓存的 job_run 状态
     *
     * @return 所有状态的副本
     */
    public Map<Long, JobRunState> getAllJobRunStates() {
        return new HashMap<>(localCache);
    }

    /**
     * 移除 job_run 状态（任务完成后清理）
     *
     * @param jobRunId 任务执行实例 ID
     */
    public void removeJobRunState(long jobRunId) {
        log.debug("移除 job_run 状态: jobRunId={}", jobRunId);
        localCache.remove(jobRunId);
    }

    /**
     * 批量移除已完成的 job_run 状态
     *
     * @param jobRunIds 要移除的 jobRunId 列表
     */
    public void batchRemoveJobRunStates(List<Long> jobRunIds) {
        if (jobRunIds == null || jobRunIds.isEmpty()) {
            return;
        }

        log.info("批量移除 {} 个 job_run 状态", jobRunIds.size());
        for (Long jobRunId : jobRunIds) {
            localCache.remove(jobRunId);
        }
    }

    /**
     * 获取最大 jobRunId
     */
    public long getLastMaxId() {
        return lastMaxId;
    }

    /**
     * 设置最大 jobRunId（从 DB 加载后更新）
     */
    public void setLastMaxId(long lastMaxId) {
        this.lastMaxId = lastMaxId;
    }

    /**
     * 获取本地缓存大小
     */
    public int getCacheSize() {
        return localCache.size();
    }

    /**
     * 清理过期的数据
     *
     * @return 清理的数据条数
     */
    public int cleanupExpiredData() {
        long now = System.currentTimeMillis();
        List<Long> expiredIds = new java.util.ArrayList<>();

        for (Map.Entry<Long, JobRunState> entry : localCache.entrySet()) {
            JobRunState state = entry.getValue();
            // 只清理已完成（终态）且超过过期时间的数据
            if (state.isTerminal() && (now - state.getLastUpdateTime() > DATA_EXPIRE_MILLIS)) {
                expiredIds.add(entry.getKey());
            }
        }

        if (!expiredIds.isEmpty()) {
            log.info("清理过期数据: count={}", expiredIds.size());
            for (Long id : expiredIds) {
                localCache.remove(id);
            }
        }

        return expiredIds.size();
    }
}
