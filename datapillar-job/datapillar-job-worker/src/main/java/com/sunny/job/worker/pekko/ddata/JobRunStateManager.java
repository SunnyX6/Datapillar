package com.sunny.job.worker.pekko.ddata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunny.job.core.enums.JobStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 任务运行状态管理器
 * <p>
 * 使用 Pekko Distributed Data (CRDT) 在集群中同步任务运行状态
 * 实现读写分离：写入 DB 后同步到 CRDT，读取从本地缓存
 * <p>
 * 数据结构: LWWMap[Long, JobRunState]
 * - Key: jobRunId
 * - Value: JobRunState (包含 status, updateTime)
 * <p>
 * 读写分离策略：
 * - 写路径: Worker → DB（持久化）→ CRDT（广播同步）
 * - 读路径: Worker → 本地 CRDT 副本（零网络开销）
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public class JobRunStateManager {

    private static final Logger log = LoggerFactory.getLogger(JobRunStateManager.class);

    private static final String JOB_RUN_STATE_KEY = "job-run-states";
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 写一致性级别：使用 WriteLocal 减少网络开销
     * <p>
     * WriteLocal: 只写本地，依赖 CRDT gossip 异步同步到其他节点
     * WriteMajority: 同步等待多数节点确认，延迟高但一致性强
     * <p>
     * 任务状态场景：最终一致性足够，使用 WriteLocal 提升性能
     */
    private final Replicator.WriteConsistency writeConsistency = Replicator.writeLocal();

    private final ActorSystem<?> system;
    private final ActorRef<Replicator.Command> replicator;
    private final SelfUniqueAddress selfUniqueAddress;

    /**
     * 本地缓存：jobRunId → JobRunState
     * <p>
     * 使用 Caffeine Cache 替代 ConcurrentHashMap，自动过期清理
     */
    private final Cache<Long, JobRunState> localCache;

    /**
     * 状态变化监听器
     */
    private volatile BiConsumer<Long, JobStatus> stateChangeListener;

    public JobRunStateManager(ActorSystem<?> system, long maxSize, Duration expireAfterWrite) {
        this.system = system;
        this.replicator = DistributedData.get(system).replicator();
        this.selfUniqueAddress = DistributedData.get(system).selfUniqueAddress();

        // 初始化 Caffeine Cache
        this.localCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWrite)
                .recordStats()
                .build();

        log.info("JobRunStateManager 初始化完成，缓存配置: maxSize={}, expireAfterWrite={}",
                maxSize, expireAfterWrite);
    }

    /**
     * 订阅任务状态变化
     * <p>
     * 注意：typed API 的 Subscribe 需要 ActorRef，这里使用本地缓存模式
     * CRDT 数据通过 Update 操作在集群中同步
     *
     * @param listener 状态变化监听器 (jobRunId, newStatus)
     */
    public void subscribe(BiConsumer<Long, JobStatus> listener) {
        this.stateChangeListener = listener;
        log.info("已设置任务运行状态变化监听器（本地模式）");
    }

    /**
     * 更新任务状态（写入 CRDT）
     * <p>
     * 调用方应先写 DB，再调用此方法同步到 CRDT
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
        log.debug("更新任务状态到 CRDT: jobRunId={}, status={}",
                state.jobRunId(), state.status());

        // 更新本地缓存
        localCache.put(state.jobRunId(), state);

        // 更新 CRDT（fire-and-forget 模式，使用 WriteLocal 减少网络开销）
        Key<LWWMap<Long, JobRunState>> key = createKey();
        replicator.tell(new Replicator.Update<>(
                key,
                LWWMap.empty(),
                writeConsistency,
                system.ignoreRef(),
                existing -> existing.put(selfUniqueAddress, state.jobRunId(), state)
        ));
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
        Key<LWWMap<Long, JobRunState>> key = createKey();

        // 批量更新本地缓存
        for (Map.Entry<Long, JobStatus> entry : updates.entrySet()) {
            JobRunState state = new JobRunState(entry.getKey(), entry.getValue(), now);
            localCache.put(entry.getKey(), state);
        }

        // 批量更新 CRDT（fire-and-forget 模式，使用 WriteLocal 减少网络开销）
        replicator.tell(new Replicator.Update<>(
                key,
                LWWMap.empty(),
                writeConsistency,
                system.ignoreRef(),
                existing -> {
                    LWWMap<Long, JobRunState> updated = existing;
                    for (Map.Entry<Long, JobStatus> entry : updates.entrySet()) {
                        JobRunState state = new JobRunState(entry.getKey(), entry.getValue(), now);
                        updated = updated.put(selfUniqueAddress, entry.getKey(), state);
                    }
                    return updated;
                }
        ));

        log.debug("批量更新 {} 个任务状态到 CRDT", updates.size());
    }

    /**
     * 获取任务状态（从本地缓存读取，零网络开销）
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
     * 创建 CRDT Key
     */
    private Key<LWWMap<Long, JobRunState>> createKey() {
        return LWWMapKey.create(JOB_RUN_STATE_KEY);
    }

    /**
     * 任务运行状态
     */
    public record JobRunState(
            long jobRunId,
            JobStatus status,
            long updateTime
    ) implements java.io.Serializable {

        private static final long serialVersionUID = 1L;
    }
}
