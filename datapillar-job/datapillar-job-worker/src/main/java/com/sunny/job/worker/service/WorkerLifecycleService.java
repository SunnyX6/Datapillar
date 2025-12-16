package com.sunny.job.worker.service;

import com.sunny.job.worker.pekko.actor.impl.JobExecutorContextImpl;
import com.sunny.job.worker.pekko.ddata.WorkerManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.typed.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Worker 生命周期服务
 * <p>
 * 职责：
 * - Worker 启动时注册本地状态
 * - Worker 关闭时清理本地状态
 * <p>
 * 注意：
 * - Worker 存活检测由 Pekko Cluster 内部心跳机制管理
 * - Bucket 再平衡由 BucketManager 监听 Cluster 事件触发
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Service
public class WorkerLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(WorkerLifecycleService.class);

    private final WorkerManager workerManager;
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

    public WorkerLifecycleService(
            WorkerManager workerManager,
            JobExecutorContextImpl executorContext,
            ActorSystem<Void> actorSystem) {
        this.workerManager = workerManager;
        this.executorContext = executorContext;
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

        workerManager.updateWorkerState(workerAddress, maxConcurrency, 0);
        registered = true;

        log.info("Worker 注册成功: address={}", workerAddress);
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

        workerManager.removeWorkerState(workerAddress);
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
