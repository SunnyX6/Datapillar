package com.sunny.job.worker.pekko.ddata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunny.job.core.enums.SplitStatus;
import com.sunny.job.core.split.SplitRange;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.ddata.Key;
import org.apache.pekko.cluster.ddata.LWWMap;
import org.apache.pekko.cluster.ddata.LWWMapKey;
import org.apache.pekko.cluster.ddata.SelfUniqueAddress;
import org.apache.pekko.cluster.ddata.typed.javadsl.DistributedData;
import org.apache.pekko.cluster.ddata.typed.javadsl.Replicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Split 状态管理器
 * <p>
 * 使用 Pekko Distributed Data (CRDT) 在集群中共享 Split 范围状态
 * <p>
 * 核心设计：
 * - Worker 自治：Worker 自己拆分、自己标记、自己执行
 * - CRDT 协调：已处理范围通过 CRDT 共享，避免重复
 * - 故障恢复：Worker 挂了，其他 Worker 从 CRDT 恢复，继续处理
 * <p>
 * 数据结构：LWWMap[String, SplitState]
 * - Key: "split-{jobRunId}-{start}-{end}"
 * - Value: SplitState (status, worker, markTime)
 *
 * @author Sunny
 */
public class SplitStateManager {

    private static final Logger log = LoggerFactory.getLogger(SplitStateManager.class);

    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 写一致性级别：使用 WriteLocal 减少网络开销
     * <p>
     * 分片状态场景：本地标记后依赖 CRDT gossip 同步，最终一致性足够
     */
    private static final Replicator.WriteConsistency WRITE_CONSISTENCY = Replicator.writeLocal();

    /**
     * 分片处理超时时间（毫秒），超时后可被其他 Worker 重新标记
     */
    private static final long PROCESSING_TIMEOUT_MS = 5 * 60 * 1000;

    private final ActorSystem<?> system;
    private final ActorRef<Replicator.Command> replicator;
    private final SelfUniqueAddress selfUniqueAddress;
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

    public SplitStateManager(ActorSystem<?> system, String selfAddress, long maxSize, Duration expireAfterWrite) {
        this.system = system;
        this.replicator = DistributedData.get(system).replicator();
        this.selfUniqueAddress = DistributedData.get(system).selfUniqueAddress();
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

        log.info("SplitStateManager 初始化完成, selfAddress={}, 缓存配置: maxSize={}, expireAfterWrite={}",
                selfAddress, maxSize, expireAfterWrite);
    }

    /**
     * 尝试标记一个分片范围为 PROCESSING
     * <p>
     * 使用乐观写入模式：
     * - 先检查本地缓存，如果已被标记且未超时，返回 false
     * - 否则直接写入 CRDT，依赖 LWWMap 的 Last-Write-Wins 语义
     *
     * @param jobRunId 任务执行实例 ID
     * @param start    分片起点
     * @param end      分片终点
     * @return true 如果标记成功（乐观），false 如果范围在本地缓存中已被标记
     */
    public boolean tryMarkProcessing(long jobRunId, long start, long end) {
        String rangeKey = buildRangeKey(jobRunId, start, end);

        // 检查本地缓存
        SplitState existing = localStateCache.getIfPresent(rangeKey);
        if (existing != null) {
            if (existing.status == SplitStatus.PROCESSING.getCode() && !isTimeout(existing.markTime)) {
                // 已被标记且未超时
                log.debug("分片范围已被标记（本地缓存）: jobRunId={}, range=[{}, {}), worker={}",
                        jobRunId, start, end, existing.worker);
                return false;
            }
            if (existing.status == SplitStatus.COMPLETED.getCode()) {
                // 已完成
                log.debug("分片范围已完成（本地缓存）: jobRunId={}, range=[{}, {})", jobRunId, start, end);
                return false;
            }
        }

        // 乐观写入 CRDT
        doMarkProcessingOptimistic(jobRunId, start, end);
        return true;
    }

    /**
     * 乐观标记操作（不等待响应）
     */
    private void doMarkProcessingOptimistic(long jobRunId, long start, long end) {
        String rangeKey = buildRangeKey(jobRunId, start, end);
        Key<LWWMap<String, SplitState>> key = createKey(jobRunId);

        SplitState newState = new SplitState(
                SplitStatus.PROCESSING.getCode(),
                selfAddress,
                System.currentTimeMillis()
        );

        // 更新本地缓存
        localStateCache.put(rangeKey, newState);

        // 更新 CRDT（fire-and-forget）
        replicator.tell(new Replicator.Update<>(
                key,
                LWWMap.empty(),
                WRITE_CONSISTENCY,
                system.ignoreRef(),
                existing -> existing.put(selfUniqueAddress, rangeKey, newState)
        ));

        log.info("标记分片范围（乐观）: jobRunId={}, range=[{}, {}), worker={}",
                jobRunId, start, end, selfAddress);
    }

    /**
     * 标记分片范围为已完成
     */
    public void markCompleted(long jobRunId, long start, long end, String message) {
        String rangeKey = buildRangeKey(jobRunId, start, end);
        Key<LWWMap<String, SplitState>> key = createKey(jobRunId);

        SplitState newState = new SplitState(
                SplitStatus.COMPLETED.getCode(),
                selfAddress,
                System.currentTimeMillis()
        );

        // 更新本地缓存
        localStateCache.put(rangeKey, newState);

        replicator.tell(new Replicator.Update<>(
                key,
                LWWMap.empty(),
                WRITE_CONSISTENCY,
                system.ignoreRef(),
                existing -> existing.put(selfUniqueAddress, rangeKey, newState)
        ));

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
        Key<LWWMap<String, SplitState>> key = createKey(jobRunId);

        SplitState newState = new SplitState(
                SplitStatus.FAILED.getCode(),
                selfAddress,
                System.currentTimeMillis()
        );

        // 更新本地缓存
        localStateCache.put(rangeKey, newState);

        replicator.tell(new Replicator.Update<>(
                key,
                LWWMap.empty(),
                WRITE_CONSISTENCY,
                system.ignoreRef(),
                existing -> existing.put(selfUniqueAddress, rangeKey, newState)
        ));

        log.warn("标记分片范围失败: jobRunId={}, range=[{}, {}), message={}",
                jobRunId, start, end, message);
    }

    /**
     * 获取下一个未处理的起点
     * <p>
     * 使用本地缓存，不查询 CRDT
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
        Key<LWWMap<String, SplitState>> key = createKey(jobRunId);
        replicator.tell(new Replicator.Delete<>(key, WRITE_CONSISTENCY, system.ignoreRef()));
        nextStartCache.invalidate(jobRunId);
        completedCountCache.invalidate(jobRunId);
        // 清理本地状态缓存（清理以该 jobRunId 开头的所有 key）
        localStateCache.asMap().keySet().removeIf(k -> k.startsWith(jobRunId + "-"));
        log.info("清理分片状态: jobRunId={}", jobRunId);
    }

    /**
     * 重置指定 Worker 的所有 PROCESSING 状态为 PENDING
     * <p>
     * 使用 fire-and-forget 模式，直接写入新状态
     */
    public void resetWorkerRanges(long jobRunId, String workerAddress) {
        log.info("重置 Worker 分片范围: jobRunId={}, worker={}", jobRunId, workerAddress);
        // 在本地缓存中查找并重置
        localStateCache.asMap().forEach((rangeKey, state) -> {
            if (rangeKey.startsWith(jobRunId + "-") &&
                    state.worker != null && state.worker.equals(workerAddress) &&
                    state.status == SplitStatus.PROCESSING.getCode()) {

                Key<LWWMap<String, SplitState>> key = createKey(jobRunId);
                SplitState newState = new SplitState(
                        SplitStatus.PENDING.getCode(),
                        null,
                        System.currentTimeMillis()
                );

                localStateCache.put(rangeKey, newState);

                replicator.tell(new Replicator.Update<>(
                        key,
                        LWWMap.empty(),
                        WRITE_CONSISTENCY,
                        system.ignoreRef(),
                        existing -> existing.put(selfUniqueAddress, rangeKey, newState)
                ));

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

    private Key<LWWMap<String, SplitState>> createKey(long jobRunId) {
        return LWWMapKey.create("split-state-" + jobRunId);
    }

    /**
     * 分片状态（CRDT 存储）
     */
    public static class SplitState implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

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
