package com.sunny.job.worker.pekko.ddata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunny.job.core.enums.JobStatus;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.ddata.Key;
import org.apache.pekko.cluster.ddata.ORMap;
import org.apache.pekko.cluster.ddata.ORMapKey;
import org.apache.pekko.cluster.ddata.LWWRegister;
import org.apache.pekko.cluster.ddata.SelfUniqueAddress;
import org.apache.pekko.cluster.ddata.typed.javadsl.DistributedData;
import org.apache.pekko.cluster.ddata.typed.javadsl.Replicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 分片状态管理器
 * <p>
 * 使用 Pekko Distributed Data (CRDT) 在集群中同步分片完成状态
 * <p>
 * 数据结构: ORMap[String, LWWRegister[Integer]]
 * - Key: "jobRunId"
 * - Value: ORMap[shardIndex, status]
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class ShardStateManager {

    private static final Logger log = LoggerFactory.getLogger(ShardStateManager.class);

    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final ActorSystem<?> system;
    private final ActorRef<Replicator.Command> replicator;
    private final SelfUniqueAddress selfUniqueAddress;

    /**
     * 本地缓存，减少 CRDT 读取次数
     * <p>
     * 使用 Caffeine Cache 替代 ConcurrentHashMap，自动过期清理
     */
    private final Cache<Long, ShardState> localCache;

    /**
     * 分片状态变化监听器
     */
    private volatile BiConsumer<Long, ShardState> stateChangeListener;

    public ShardStateManager(ActorSystem<?> system, long maxSize, Duration expireAfterWrite) {
        this.system = system;
        this.replicator = DistributedData.get(system).replicator();
        this.selfUniqueAddress = DistributedData.get(system).selfUniqueAddress();

        // 初始化 Caffeine Cache
        this.localCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWrite)
                .build();

        log.info("ShardStateManager 初始化完成，缓存配置: maxSize={}, expireAfterWrite={}",
                maxSize, expireAfterWrite);
    }

    /**
     * 订阅分片状态变化
     *
     * @param listener 状态变化监听器 (jobRunId, shardState)
     */
    public void subscribe(BiConsumer<Long, ShardState> listener) {
        this.stateChangeListener = listener;
        log.info("已设置分片状态变化监听器");
    }

    /**
     * 记录分片完成状态
     *
     * @param jobRunId   任务执行实例 ID
     * @param shardIndex 分片索引
     * @param shardTotal 分片总数
     * @param status     分片状态
     */
    public void recordShardComplete(long jobRunId, int shardIndex, int shardTotal, JobStatus status) {
        log.debug("记录分片完成: jobRunId={}, shardIndex={}/{}, status={}",
                jobRunId, shardIndex, shardTotal, status);

        // 更新本地缓存
        ShardState state = localCache.get(Long.valueOf(jobRunId), k -> new ShardState(shardTotal));
        state.setShardStatus(shardIndex, status);

        // 通知监听器
        if (stateChangeListener != null) {
            stateChangeListener.accept(jobRunId, state);
        }

        // 更新 CRDT（fire-and-forget 模式）
        Key<ORMap<String, LWWRegister<Integer>>> key = createKey(jobRunId);
        String shardKey = String.valueOf(shardIndex);

        replicator.tell(new Replicator.Update<>(
                key,
                ORMap.empty(),
                new Replicator.WriteMajority(WRITE_TIMEOUT),
                system.ignoreRef(),
                existing -> existing.update(
                        selfUniqueAddress,
                        shardKey,
                        LWWRegister.create(selfUniqueAddress, status.getCode()),
                        reg -> LWWRegister.create(selfUniqueAddress, status.getCode())
                )
        ));
    }

    /**
     * 检查是否所有分片都已完成
     *
     * @param jobRunId   任务执行实例 ID
     * @param shardTotal 分片总数
     * @return true 如果所有分片都已完成（状态为终态）
     */
    public boolean checkAllShardsCompleted(long jobRunId, int shardTotal) {
        ShardState state = localCache.getIfPresent(jobRunId);
        if (state == null) {
            log.debug("分片状态不存在: jobRunId={}", jobRunId);
            return false;
        }

        boolean allCompleted = state.isAllCompleted();
        log.debug("检查分片完成: jobRunId={}, completed={}/{}, allCompleted={}",
                jobRunId, state.getCompletedCount(), shardTotal, allCompleted);

        return allCompleted;
    }

    /**
     * 汇聚分片结果，计算最终状态
     * <p>
     * 规则：全部成功才成功，任一失败/超时则失败
     *
     * @param jobRunId   任务执行实例 ID
     * @param shardTotal 分片总数
     * @return 汇聚后的最终状态
     */
    public JobStatus aggregateShardResults(long jobRunId, int shardTotal) {
        ShardState state = localCache.getIfPresent(jobRunId);
        if (state == null) {
            log.warn("分片状态不存在，返回 FAIL: jobRunId={}", jobRunId);
            return JobStatus.FAIL;
        }

        JobStatus finalStatus = state.aggregate();
        log.info("汇聚分片结果: jobRunId={}, shardTotal={}, finalStatus={}",
                jobRunId, shardTotal, finalStatus);

        // 清理本地缓存
        localCache.invalidate(jobRunId);

        // 清理 CRDT 数据
        cleanupShardData(jobRunId);

        return finalStatus;
    }

    /**
     * 清理分片数据
     */
    private void cleanupShardData(long jobRunId) {
        Key<ORMap<String, LWWRegister<Integer>>> key = createKey(jobRunId);
        replicator.tell(new Replicator.Delete<>(key, new Replicator.WriteMajority(WRITE_TIMEOUT), system.ignoreRef()));
        log.debug("清理分片数据: jobRunId={}", jobRunId);
    }

    /**
     * 从 CRDT 恢复分片状态
     * <p>
     * 使用本地缓存，不查询 CRDT（因 typed API 不支持 lambda 回调）
     *
     * @param jobRunId   任务执行实例 ID
     * @param shardTotal 分片总数
     * @return 恢复的分片状态，不存在返回 null
     */
    public ShardState recoverShardState(long jobRunId, int shardTotal) {
        log.info("尝试从本地缓存恢复分片状态: jobRunId={}", jobRunId);
        ShardState state = localCache.getIfPresent(jobRunId);
        if (state == null) {
            log.debug("本地缓存中没有分片状态: jobRunId={}", jobRunId);
        }
        return state;
    }

    /**
     * 获取本地缓存中的分片状态
     *
     * @param jobRunId 任务执行实例 ID
     * @return 分片状态，不存在返回 null
     */
    public ShardState getShardState(long jobRunId) {
        return localCache.getIfPresent(jobRunId);
    }

    /**
     * 创建 CRDT Key
     */
    private Key<ORMap<String, LWWRegister<Integer>>> createKey(long jobRunId) {
        return ORMapKey.create("shard-state-" + jobRunId);
    }

    /**
     * 分片状态（本地缓存）
     */
    public static class ShardState {
        private final int shardTotal;
        private final Map<Integer, JobStatus> shardStatuses;

        ShardState(int shardTotal) {
            this.shardTotal = shardTotal;
            this.shardStatuses = new HashMap<>();
        }

        void setShardStatus(int shardIndex, JobStatus status) {
            shardStatuses.put(shardIndex, status);
        }

        int getCompletedCount() {
            return (int) shardStatuses.values().stream()
                    .filter(JobStatus::isTerminal)
                    .count();
        }

        boolean isAllCompleted() {
            return getCompletedCount() >= shardTotal;
        }

        JobStatus aggregate() {
            if (shardStatuses.isEmpty()) {
                return JobStatus.FAIL;
            }

            // 检查是否有非终态
            boolean hasRunning = shardStatuses.values().stream()
                    .anyMatch(s -> !s.isTerminal());
            if (hasRunning) {
                return JobStatus.RUNNING;
            }

            // 全部成功才成功
            boolean allSuccess = shardStatuses.values().stream()
                    .allMatch(JobStatus::isSuccess);
            if (allSuccess) {
                return JobStatus.SUCCESS;
            }

            // 有超时则超时
            boolean hasTimeout = shardStatuses.values().stream()
                    .anyMatch(s -> s == JobStatus.TIMEOUT);
            if (hasTimeout) {
                return JobStatus.TIMEOUT;
            }

            return JobStatus.FAIL;
        }
    }
}
