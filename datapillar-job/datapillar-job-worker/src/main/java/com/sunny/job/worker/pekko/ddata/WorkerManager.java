package com.sunny.job.worker.pekko.ddata;

import com.sunny.job.core.strategy.route.WorkerInfo;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.Member;
import org.apache.pekko.cluster.MemberStatus;
import org.apache.pekko.cluster.typed.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Worker 状态管理器
 * <p>
 * 使用 Pekko Cluster 成员列表获取存活 Worker，本地缓存负载信息
 * <p>
 * 设计说明：
 * - 存活 Worker 列表：从 Pekko Cluster 成员列表获取（零额外开销）
 * - 负载信息：本地缓存，每个 Worker 只知道自己的负载
 * - 不使用 CRDT：避免序列化问题，减少网络开销
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public class WorkerManager {

    private static final Logger log = LoggerFactory.getLogger(WorkerManager.class);

    /**
     * Worker 心跳超时时间（毫秒）
     */
    private static final long WORKER_HEARTBEAT_TIMEOUT = 60_000;

    private final ActorSystem<?> system;
    private final Cluster cluster;
    private final String selfAddress;

    /**
     * 本地缓存：只存储本 Worker 的状态
     */
    private volatile WorkerState selfState;

    /**
     * 状态变化监听器
     */
    private volatile Consumer<WorkerState> stateChangeListener;

    public WorkerManager(ActorSystem<?> system) {
        this.system = system;
        this.cluster = Cluster.get(system);
        this.selfAddress = cluster.selfMember().address().toString();

        log.info("WorkerManager 初始化完成，使用 Pekko Cluster 成员列表，selfAddress={}", selfAddress);
    }

    /**
     * 订阅 Worker 状态变化
     *
     * @param listener 状态变化监听器
     */
    public void subscribe(Consumer<WorkerState> listener) {
        this.stateChangeListener = listener;
        log.info("已设置 Worker 状态变化监听器");
    }

    /**
     * 更新 Worker 状态（只更新本 Worker 的状态）
     *
     * @param address        Worker 地址
     * @param maxConcurrency 最大并发数
     * @param currentRunning 当前运行任务数
     */
    public void updateWorkerState(String address, int maxConcurrency, int currentRunning) {
        // 只更新本 Worker 的状态
        if (!address.equals(selfAddress)) {
            log.debug("忽略非本 Worker 的状态更新: address={}", address);
            return;
        }

        WorkerState state = new WorkerState(address, maxConcurrency, currentRunning, System.currentTimeMillis());
        updateWorkerState(state);
    }

    /**
     * 更新 Worker 状态
     *
     * @param state Worker 状态
     */
    public void updateWorkerState(WorkerState state) {
        log.debug("更新本 Worker 状态: running={}/{}",
                state.currentRunning(), state.maxConcurrency());

        this.selfState = state;

        // 通知监听器
        if (stateChangeListener != null) {
            stateChangeListener.accept(state);
        }
    }

    /**
     * 移除 Worker 状态（Worker 下线时）
     *
     * @param address Worker 地址
     */
    public void removeWorkerState(String address) {
        if (address.equals(selfAddress)) {
            log.info("清除本 Worker 状态: address={}", address);
            this.selfState = null;
        }
    }

    /**
     * 获取所有存活的 Worker
     * <p>
     * 从 Pekko Cluster 成员列表获取，只返回状态为 Up 的成员
     *
     * @return Worker 信息列表
     */
    public List<WorkerInfo> getAliveWorkers() {
        List<WorkerInfo> aliveWorkers = new ArrayList<>();

        // 从 Cluster 成员列表获取存活 Worker
        scala.collection.immutable.SortedSet<Member> members = cluster.state().members();
        scala.collection.Iterator<Member> iterator = members.iterator();

        while (iterator.hasNext()) {
            Member member = iterator.next();
            // 只返回状态为 Up 的成员
            if (member.status() == MemberStatus.up()) {
                String address = member.address().toString();
                // 使用默认负载信息（因为我们不同步负载）
                aliveWorkers.add(WorkerInfo.of(address));
            }
        }

        log.debug("获取存活 Worker 列表: count={}", aliveWorkers.size());
        return aliveWorkers;
    }

    /**
     * 获取所有 Worker 状态
     * <p>
     * 注意：只返回本 Worker 的状态，因为不再同步其他 Worker 的负载信息
     *
     * @return Worker 状态列表
     */
    public List<WorkerState> getAllWorkerStates() {
        List<WorkerState> states = new ArrayList<>();
        if (selfState != null) {
            states.add(selfState);
        }
        return states;
    }

    /**
     * 获取指定 Worker 状态
     *
     * @param address Worker 地址
     * @return Worker 状态，不存在返回 null
     */
    public WorkerState getWorkerState(String address) {
        if (address.equals(selfAddress) && selfState != null) {
            return selfState;
        }
        return null;
    }

    /**
     * 获取本 Worker 状态
     *
     * @return 本 Worker 状态
     */
    public WorkerState getSelfState() {
        return selfState;
    }

    /**
     * 获取自身地址
     *
     * @return Worker 地址
     */
    public String getSelfAddress() {
        return selfAddress;
    }

    /**
     * 清理超时的 Worker（不再需要，由 Cluster 自动管理）
     *
     * @return 清理的 Worker 数量
     */
    public int cleanupExpiredWorkers() {
        // Pekko Cluster 自动管理成员状态，不需要手动清理
        return 0;
    }

    /**
     * Worker 状态
     */
    public record WorkerState(
            String address,
            int maxConcurrency,
            int currentRunning,
            long lastHeartbeat
    ) {
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
