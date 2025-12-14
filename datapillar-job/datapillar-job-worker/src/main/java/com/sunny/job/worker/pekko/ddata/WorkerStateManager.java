package com.sunny.job.worker.pekko.ddata;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunny.job.core.strategy.route.WorkerInfo;
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
import java.util.function.Consumer;

/**
 * Worker 状态管理器
 * <p>
 * 使用 Pekko Distributed Data (CRDT) 在集群中同步 Worker 负载信息
 * <p>
 * 数据结构: LWWMap[String, WorkerState]
 * - Key: Worker 地址 (ip:port)
 * - Value: WorkerState (包含 maxConcurrency, currentRunning, lastHeartbeat)
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class WorkerStateManager {

    private static final Logger log = LoggerFactory.getLogger(WorkerStateManager.class);

    private static final String WORKER_STATE_KEY = "worker-states";
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(3);

    /**
     * Worker 心跳超时时间（毫秒）
     */
    private static final long WORKER_HEARTBEAT_TIMEOUT = 60_000;

    private final ActorSystem<?> system;
    private final ActorRef<Replicator.Command> replicator;
    private final SelfUniqueAddress selfUniqueAddress;

    /**
     * 本地缓存
     * <p>
     * 使用 Caffeine Cache 替代 ConcurrentHashMap，自动过期清理
     */
    private final Cache<String, WorkerState> localCache;

    /**
     * 状态变化监听器
     */
    private volatile Consumer<WorkerState> stateChangeListener;

    public WorkerStateManager(ActorSystem<?> system, long maxSize, Duration expireAfterWrite) {
        this.system = system;
        this.replicator = DistributedData.get(system).replicator();
        this.selfUniqueAddress = DistributedData.get(system).selfUniqueAddress();

        // 初始化 Caffeine Cache
        this.localCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWrite)
                .build();

        log.info("WorkerStateManager 初始化完成，缓存配置: maxSize={}, expireAfterWrite={}",
                maxSize, expireAfterWrite);
    }

    /**
     * 订阅 Worker 状态变化
     * <p>
     * 注意：typed API 的 Subscribe 需要 ActorRef，这里使用本地缓存 + 定期同步模式
     * CRDT 数据通过 Update 操作在集群中同步，本地缓存在 Update 时更新
     *
     * @param listener 状态变化监听器
     */
    public void subscribe(Consumer<WorkerState> listener) {
        this.stateChangeListener = listener;
        log.info("已设置 Worker 状态变化监听器（本地模式）");
    }

    /**
     * 更新 Worker 状态
     *
     * @param address        Worker 地址
     * @param maxConcurrency 最大并发数
     * @param currentRunning 当前运行任务数
     */
    public void updateWorkerState(String address, int maxConcurrency, int currentRunning) {
        WorkerState state = new WorkerState(address, maxConcurrency, currentRunning, System.currentTimeMillis());
        updateWorkerState(state);
    }

    /**
     * 更新 Worker 状态
     *
     * @param state Worker 状态
     */
    public void updateWorkerState(WorkerState state) {
        log.debug("更新 Worker 状态: address={}, running={}/{}",
                state.address(), state.currentRunning(), state.maxConcurrency());

        // 更新本地缓存
        localCache.put(state.address(), state);

        // 更新 CRDT（fire-and-forget 模式，使用 ignoreRef 忽略响应）
        Key<LWWMap<String, WorkerState>> key = createKey();

        replicator.tell(new Replicator.Update<>(
                key,
                LWWMap.empty(),
                new Replicator.WriteMajority(WRITE_TIMEOUT),
                system.ignoreRef(),
                existing -> existing.put(selfUniqueAddress, state.address(), state)
        ));
    }

    /**
     * 移除 Worker 状态（Worker 下线时）
     *
     * @param address Worker 地址
     */
    public void removeWorkerState(String address) {
        log.info("移除 Worker 状态: address={}", address);

        // 移除本地缓存
        localCache.invalidate(address);

        // 从 CRDT 移除（fire-and-forget 模式）
        Key<LWWMap<String, WorkerState>> key = createKey();

        replicator.tell(new Replicator.Update<>(
                key,
                LWWMap.empty(),
                new Replicator.WriteMajority(WRITE_TIMEOUT),
                system.ignoreRef(),
                existing -> existing.remove(selfUniqueAddress, address)
        ));
    }

    /**
     * 获取所有存活的 Worker
     *
     * @return Worker 信息列表
     */
    public List<WorkerInfo> getAliveWorkers() {
        long now = System.currentTimeMillis();
        List<WorkerInfo> aliveWorkers = new ArrayList<>();

        for (WorkerState state : localCache.asMap().values()) {
            if (now - state.lastHeartbeat() < WORKER_HEARTBEAT_TIMEOUT) {
                aliveWorkers.add(state.toWorkerInfo());
            }
        }

        return aliveWorkers;
    }

    /**
     * 获取所有 Worker 状态
     *
     * @return Worker 状态列表
     */
    public List<WorkerState> getAllWorkerStates() {
        return new ArrayList<>(localCache.asMap().values());
    }

    /**
     * 获取指定 Worker 状态
     *
     * @param address Worker 地址
     * @return Worker 状态，不存在返回 null
     */
    public WorkerState getWorkerState(String address) {
        return localCache.getIfPresent(address);
    }

    /**
     * 清理超时的 Worker
     *
     * @return 清理的 Worker 数量
     */
    public int cleanupExpiredWorkers() {
        long now = System.currentTimeMillis();
        List<String> expiredAddresses = new ArrayList<>();

        for (Map.Entry<String, WorkerState> entry : localCache.asMap().entrySet()) {
            if (now - entry.getValue().lastHeartbeat() > WORKER_HEARTBEAT_TIMEOUT * 2) {
                expiredAddresses.add(entry.getKey());
            }
        }

        for (String address : expiredAddresses) {
            removeWorkerState(address);
        }

        if (!expiredAddresses.isEmpty()) {
            log.info("清理超时 Worker: count={}", expiredAddresses.size());
        }

        return expiredAddresses.size();
    }

    /**
     * 创建 CRDT Key
     */
    private Key<LWWMap<String, WorkerState>> createKey() {
        return LWWMapKey.create(WORKER_STATE_KEY);
    }

    /**
     * Worker 状态
     */
    public record WorkerState(
            String address,
            int maxConcurrency,
            int currentRunning,
            long lastHeartbeat
    ) implements java.io.Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 获取可用容量
         */
        public int availableCapacity() {
            return Math.max(0, maxConcurrency - currentRunning);
        }

        /**
         * 是否有可用容量
         */
        public boolean hasCapacity() {
            return maxConcurrency <= 0 || currentRunning < maxConcurrency;
        }

        /**
         * 转换为 WorkerInfo
         */
        public WorkerInfo toWorkerInfo() {
            return new WorkerInfo(address, maxConcurrency, currentRunning, lastHeartbeat);
        }
    }
}
