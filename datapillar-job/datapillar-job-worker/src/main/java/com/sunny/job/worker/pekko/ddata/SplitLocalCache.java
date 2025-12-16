package com.sunny.job.worker.pekko.ddata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunny.job.core.enums.SplitStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Split 本地缓存
 * <p>
 * 管理 Split 范围的处理状态，使用本地缓存
 * 不使用 CRDT（在去中心化架构下，每个 Worker 独占自己 Bucket 的任务，不需要跨 Worker 同步）
 * <p>
 * 核心设计：
 * - Worker 自治：Worker 自己拆分、自己标记、自己执行
 * - 本地缓存：状态只在本地缓存，不跨 Worker 同步
 * <p>
 * 数据结构：Cache[String, SplitState]
 * - Key: "{jobRunId}-{start}-{end}"
 * - Value: SplitState (status, worker, markTime)
 *
 * @author Sunny
 * @date 2025-12-15
 */
public class SplitLocalCache {

    private static final Logger log = LoggerFactory.getLogger(SplitLocalCache.class);

    /**
     * 分片处理超时时间（毫秒），超时后可被重新处理
     */
    private static final long PROCESSING_TIMEOUT_MS = 5 * 60 * 1000;

    private final String selfAddress;

    /**
     * 本地缓存：jobRunId -> nextStart（下一个未处理的起点）
     */
    private final Cache<Long, Long> nextStartCache;

    /**
     * 本地缓存：jobRunId -> completedRanges（已完成的范围数）
     */
    private final Cache<Long, Long> completedCountCache;

    /**
     * 本地缓存：rangeKey -> SplitState
     */
    private final Cache<String, SplitState> localStateCache;

    public SplitLocalCache(String selfAddress, long maxSize, Duration expireAfterWrite) {
        this.selfAddress = selfAddress;

        // 初始化 Caffeine Cache
        this.nextStartCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWrite)
                .build();

        this.completedCountCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWrite)
                .build();

        this.localStateCache = Caffeine.newBuilder()
                .maximumSize(maxSize * 10)
                .expireAfterWrite(expireAfterWrite)
                .build();

        log.info("SplitLocalCache 初始化完成, selfAddress={}, 缓存配置: maxSize={}, expireAfterWrite={}",
                selfAddress, maxSize, expireAfterWrite);
    }

    /**
     * 尝试标记一个分片范围为 PROCESSING
     *
     * @param jobRunId 任务执行实例 ID
     * @param start    分片起点
     * @param end      分片终点
     * @return true 如果标记成功，false 如果范围已被标记
     */
    public boolean tryMarkProcessing(long jobRunId, long start, long end) {
        String rangeKey = buildRangeKey(jobRunId, start, end);

        // 检查本地缓存
        SplitState existing = localStateCache.getIfPresent(rangeKey);
        if (existing != null) {
            if (existing.status == SplitStatus.PROCESSING.getCode() && !isTimeout(existing.markTime)) {
                log.debug("分片范围已被标记: jobRunId={}, range=[{}, {}), worker={}",
                        jobRunId, start, end, existing.worker);
                return false;
            }
            if (existing.status == SplitStatus.COMPLETED.getCode()) {
                log.debug("分片范围已完成: jobRunId={}, range=[{}, {})", jobRunId, start, end);
                return false;
            }
        }

        // 标记为 PROCESSING
        SplitState newState = new SplitState(
                SplitStatus.PROCESSING.getCode(),
                selfAddress,
                System.currentTimeMillis()
        );
        localStateCache.put(rangeKey, newState);

        log.info("标记分片范围: jobRunId={}, range=[{}, {}), worker={}",
                jobRunId, start, end, selfAddress);
        return true;
    }

    /**
     * 标记分片范围为已完成
     */
    public void markCompleted(long jobRunId, long start, long end, String message) {
        String rangeKey = buildRangeKey(jobRunId, start, end);

        SplitState newState = new SplitState(
                SplitStatus.COMPLETED.getCode(),
                selfAddress,
                System.currentTimeMillis()
        );
        localStateCache.put(rangeKey, newState);

        // 更新完成计数缓存
        Long currentCount = completedCountCache.getIfPresent(jobRunId);
        completedCountCache.put(jobRunId, currentCount == null ? 1L : currentCount + 1L);

        log.info("标记分片范围完成: jobRunId={}, range=[{}, {}), message={}",
                jobRunId, start, end, message);
    }

    /**
     * 标记分片范围为失败
     */
    public void markFailed(long jobRunId, long start, long end, String message) {
        String rangeKey = buildRangeKey(jobRunId, start, end);

        SplitState newState = new SplitState(
                SplitStatus.FAILED.getCode(),
                selfAddress,
                System.currentTimeMillis()
        );
        localStateCache.put(rangeKey, newState);

        log.warn("标记分片范围失败: jobRunId={}, range=[{}, {}), message={}",
                jobRunId, start, end, message);
    }

    /**
     * 获取下一个未处理的起点
     *
     * @param jobRunId 任务执行实例 ID
     * @return 下一个未处理的起点，如果没有缓存返回 0
     */
    public long getNextStart(long jobRunId) {
        Long cached = nextStartCache.getIfPresent(jobRunId);
        return cached != null ? cached : 0;
    }

    /**
     * 更新下一个起点缓存
     */
    public void updateNextStart(long jobRunId, long nextStart) {
        nextStartCache.put(jobRunId, nextStart);
    }

    /**
     * 清理 Job 的分片状态
     */
    public void cleanup(long jobRunId) {
        nextStartCache.invalidate(jobRunId);
        completedCountCache.invalidate(jobRunId);
        // 清理本地状态缓存（清理以该 jobRunId 开头的所有 key）
        localStateCache.asMap().keySet().removeIf(k -> k.startsWith(jobRunId + "-"));
        log.info("清理分片状态: jobRunId={}", jobRunId);
    }

    /**
     * 重置指定 Worker 的所有 PROCESSING 状态为 PENDING
     */
    public void resetWorkerRanges(long jobRunId, String workerAddress) {
        log.info("重置 Worker 分片范围: jobRunId={}, worker={}", jobRunId, workerAddress);

        localStateCache.asMap().forEach((rangeKey, state) -> {
            if (rangeKey.startsWith(jobRunId + "-") &&
                    state.worker != null && state.worker.equals(workerAddress) &&
                    state.status == SplitStatus.PROCESSING.getCode()) {

                SplitState newState = new SplitState(
                        SplitStatus.PENDING.getCode(),
                        null,
                        System.currentTimeMillis()
                );
                localStateCache.put(rangeKey, newState);

                log.info("重置分片范围: jobRunId={}, range={}, oldWorker={}",
                        jobRunId, rangeKey, workerAddress);
            }
        });
    }

    private boolean isTimeout(long markTime) {
        return System.currentTimeMillis() - markTime > PROCESSING_TIMEOUT_MS;
    }

    private String buildRangeKey(long jobRunId, long start, long end) {
        return jobRunId + "-" + start + "-" + end;
    }

    /**
     * 分片状态
     */
    public static class SplitState {
        public final int status;
        public final String worker;
        public final long markTime;

        public SplitState(int status, String worker, long markTime) {
            this.status = status;
            this.worker = worker;
            this.markTime = markTime;
        }
    }
}
