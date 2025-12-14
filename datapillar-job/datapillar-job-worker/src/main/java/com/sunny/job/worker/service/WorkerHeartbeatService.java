package com.sunny.job.worker.service;

import com.sunny.job.worker.pekko.actor.impl.JobExecutorContextImpl;
import com.sunny.job.worker.pekko.ddata.BucketStateManager;
import com.sunny.job.worker.pekko.ddata.WorkerStateManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.typed.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Worker 心跳服务
 * <p>
 * 负责定期向集群同步当前 Worker 的负载信息，用于：
 * 1. 让 Dispatcher 知道 Worker 存活状态
 * 2. 让路由策略（如 LEAST_BUSY）选择最空闲的 Worker
 * 3. 触发 Bucket 再平衡（Worker 数量变化时）
 * <p>
 * 核心逻辑：
 * 1. Worker 启动时注册自己到 CRDT
 * 2. 定时（默认 10 秒）更新负载信息（currentRunning）
 * 3. 检测 Worker 数量变化，触发 Bucket 再平衡
 * 4. Worker 关闭时从 CRDT 注销
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class WorkerHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(WorkerHeartbeatService.class);

    private final WorkerStateManager workerStateManager;
    private final BucketStateManager bucketStateManager;
    private final JobExecutorContextImpl executorContext;

    /**
     * Worker 地址（用于标识）
     */
    private final String workerAddress;

    /**
     * 最大并发数
     */
    private final int maxConcurrency;

    /**
     * 是否已注册
     */
    private volatile boolean registered = false;

    /**
     * 上次检测到的 Worker 数量（用于检测变化）
     */
    private volatile int lastWorkerCount = 0;

    /**
     * 心跳计数器（每 N 次心跳检查一次 Bucket 再平衡，减少开销）
     */
    private int heartbeatCounter = 0;

    /**
     * 再平衡检查间隔（每 3 次心跳检查一次，即 30 秒）
     */
    private static final int REBALANCE_CHECK_INTERVAL = 3;

    public WorkerHeartbeatService(
            WorkerStateManager workerStateManager,
            BucketStateManager bucketStateManager,
            JobExecutorContextImpl executorContext,
            ActorSystem<Void> actorSystem) {
        this.workerStateManager = workerStateManager;
        this.bucketStateManager = bucketStateManager;
        this.executorContext = executorContext;
        // 使用 Pekko Cluster 地址，保证与 BucketStateManager 一致
        Cluster cluster = Cluster.get(actorSystem);
        this.workerAddress = cluster.selfMember().address().toString();
        this.maxConcurrency = executorContext.getMaxConcurrentTasks();
    }

    /**
     * Worker 启动时注册
     */
    @PostConstruct
    public void register() {
        log.info("Worker 启动注册: address={}, maxConcurrency={}", workerAddress, maxConcurrency);

        workerStateManager.updateWorkerState(workerAddress, maxConcurrency, 0);
        registered = true;

        // 订阅 Worker 状态变化（可选，用于监控）
        workerStateManager.subscribe(state -> {
            log.debug("Worker 状态变化通知: address={}, running={}/{}",
                    state.address(), state.currentRunning(), state.maxConcurrency());
        });

        log.info("Worker 注册成功: address={}", workerAddress);
    }

    /**
     * 定时心跳（每 10 秒）
     * <p>
     * 更新当前 Worker 的负载信息到 CRDT，并检测 Worker 数量变化触发再平衡
     */
    @Scheduled(fixedRate = 10000, initialDelay = 10000)
    public void heartbeat() {
        if (!registered) {
            return;
        }

        int currentRunning = executorContext.getRunningTaskCount();

        log.debug("Worker 心跳上报: address={}, running={}/{}", workerAddress, currentRunning, maxConcurrency);

        workerStateManager.updateWorkerState(workerAddress, maxConcurrency, currentRunning);

        // 每 N 次心跳检测一次 Worker 数量变化，减少开销
        heartbeatCounter++;
        if (heartbeatCounter >= REBALANCE_CHECK_INTERVAL) {
            heartbeatCounter = 0;
            checkAndRebalanceBuckets();
        }
    }

    /**
     * 检测 Worker 数量变化，触发 Bucket 再平衡
     * <p>
     * 基于一致性哈希的再平衡：
     * - Worker 数量变化时，重新计算 Bucket 归属
     * - 只迁移 bucketCount/N 个 Bucket，最小化影响
     */
    private void checkAndRebalanceBuckets() {
        int currentWorkerCount = workerStateManager.getAliveWorkers().size();

        if (currentWorkerCount != lastWorkerCount && currentWorkerCount > 0) {
            log.info("检测到 Worker 数量变化: {} -> {}, 触发 Bucket 再平衡",
                    lastWorkerCount, currentWorkerCount);

            lastWorkerCount = currentWorkerCount;
            bucketStateManager.rebalanceBuckets();
        }
    }

    /**
     * Worker 关闭时注销
     */
    @PreDestroy
    public void unregister() {
        if (!registered) {
            return;
        }

        log.info("Worker 关闭注销: address={}", workerAddress);

        workerStateManager.removeWorkerState(workerAddress);
        registered = false;

        log.info("Worker 注销成功: address={}", workerAddress);
    }

    /**
     * 获取 Worker 地址
     */
    public String getWorkerAddress() {
        return workerAddress;
    }

    /**
     * 获取最大并发数
     */
    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    /**
     * 获取当前运行任务数
     */
    public int getCurrentRunning() {
        return executorContext.getRunningTaskCount();
    }

    /**
     * 获取可用容量
     */
    public int getAvailableCapacity() {
        return Math.max(0, maxConcurrency - getCurrentRunning());
    }

    /**
     * 是否有可用容量
     */
    public boolean hasCapacity() {
        return getCurrentRunning() < maxConcurrency;
    }
}
