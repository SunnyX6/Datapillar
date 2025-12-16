package com.sunny.job.worker.pekko.actor;

import com.sunny.job.core.message.SchedulerMessage;
import com.sunny.job.worker.pekko.ddata.BucketManager;
import com.sunny.job.worker.pekko.ddata.JobRunLocalCache;
import com.sunny.job.worker.pekko.ddata.MaxJobRunIdState;
import com.sunny.job.worker.pekko.ddata.SplitLocalCache;
import com.sunny.job.worker.service.JobPreloadService;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.DispatcherSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * JobScheduler 管理器（分片调度）
 * <p>
 * 核心设计：
 * - 创建 N 个 JobScheduler Actor，并行调度
 * - 每个 Scheduler 负责 bucketCount/N 个 Bucket
 * - 按 bucketId % shardCount 路由到对应 Scheduler
 * <p>
 * 性能提升：
 * - 单 Actor → N Actor，调度吞吐量提升 N 倍
 * - 每个 Scheduler 有独立的 PriorityQueue，无竞争
 * - Bucket 变更事件自动路由到对应 Scheduler
 * - 集成预加载服务，同步 Bucket 变更
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public class JobSchedulerManager {

    private static final Logger log = LoggerFactory.getLogger(JobSchedulerManager.class);

    private final int shardCount;
    private final List<ActorRef<SchedulerMessage>> schedulers;
    private final BucketManager bucketManager;
    private final MaxJobRunIdState maxJobRunIdManager;
    private final JobPreloadService preloadService;

    /**
     * 创建分片调度管理器
     *
     * @param actorSystem         Actor 系统
     * @param shardCount          分片数量（建议 = CPU 核心数）
     * @param schedulerContext    调度上下文
     * @param executorContext     执行上下文
     * @param bucketManager  Bucket 状态管理器
     * @param splitLocalCache   分片状态管理器
     * @param jobRunLocalCache  任务运行状态管理器
     * @param maxJobRunIdManager  最大 JobRunId 管理器
     * @param preloadService      预加载服务
     * @param workerAddress       Worker 地址
     */
    public JobSchedulerManager(ActorSystem<Void> actorSystem,
                                int shardCount,
                                JobSchedulerContext schedulerContext,
                                JobExecutorContext executorContext,
                                BucketManager bucketManager,
                                SplitLocalCache splitLocalCache,
                                JobRunLocalCache jobRunLocalCache,
                                MaxJobRunIdState maxJobRunIdManager,
                                JobPreloadService preloadService,
                                String workerAddress) {
        this.shardCount = shardCount;
        this.schedulers = new ArrayList<>(shardCount);
        this.bucketManager = bucketManager;
        this.maxJobRunIdManager = maxJobRunIdManager;
        this.preloadService = preloadService;

        log.info("初始化分片调度管理器，shardCount={}", shardCount);

        // 创建 N 个 JobScheduler Actor
        for (int i = 0; i < shardCount; i++) {
            ActorRef<SchedulerMessage> scheduler = actorSystem.systemActorOf(
                    JobScheduler.create(
                            schedulerContext,
                            executorContext,
                            bucketManager,
                            splitLocalCache,
                            jobRunLocalCache,
                            maxJobRunIdManager,
                            workerAddress,
                            i,           // shardId
                            shardCount   // 总分片数
                    ),
                    "job-scheduler-" + i,
                    DispatcherSelector.fromConfig("pekko.actor.job-scheduler-dispatcher")
            );
            schedulers.add(scheduler);
            log.info("创建 JobScheduler-{}", i);
        }

        // 订阅 Bucket 变更，路由到对应 Scheduler
        setupBucketRouting();

        // 订阅 maxJobRunId 变化，广播给所有 Scheduler
        setupMaxJobRunIdListener();

        log.info("分片调度管理器初始化完成，共 {} 个 Scheduler", shardCount);
    }

    /**
     * 设置 Bucket 变更路由
     * <p>
     * Bucket 获得/丢失事件自动路由到负责该 Bucket 的 Scheduler
     * 同时同步给预加载服务
     */
    private void setupBucketRouting() {
        bucketManager.subscribe(
                bucketId -> {
                    // Bucket 获得 → 路由到对应 Scheduler
                    ActorRef<SchedulerMessage> scheduler = getSchedulerForBucket(bucketId);
                    scheduler.tell(new SchedulerMessage.BucketAcquired(bucketId));
                    // 同步给预加载服务
                    if (preloadService != null) {
                        preloadService.updateBucket(bucketId, true);
                    }
                },
                bucketId -> {
                    // Bucket 丢失 → 路由到对应 Scheduler
                    ActorRef<SchedulerMessage> scheduler = getSchedulerForBucket(bucketId);
                    scheduler.tell(new SchedulerMessage.BucketLost(bucketId));
                    // 同步给预加载服务
                    if (preloadService != null) {
                        preloadService.updateBucket(bucketId, false);
                    }
                }
        );
    }

    /**
     * 设置 maxJobRunId 变化监听
     * <p>
     * 当全局 maxJobRunId 变化时，广播给所有 Scheduler 触发增量加载
     */
    private void setupMaxJobRunIdListener() {
        maxJobRunIdManager.subscribe(newMaxId -> {
            log.info("检测到全局 maxJobRunId 变化: {}，广播给所有 Scheduler", newMaxId);
            broadcast(new SchedulerMessage.GlobalMaxIdChanged(newMaxId));
        });
    }

    /**
     * 根据 bucketId 获取对应的 Scheduler
     */
    public ActorRef<SchedulerMessage> getSchedulerForBucket(int bucketId) {
        int shardId = bucketId % shardCount;
        return schedulers.get(shardId);
    }

    /**
     * 获取所有 Scheduler
     */
    public List<ActorRef<SchedulerMessage>> getAllSchedulers() {
        return List.copyOf(schedulers);
    }

    /**
     * 获取分片数量
     */
    public int getShardCount() {
        return shardCount;
    }

    /**
     * 向所有 Scheduler 广播消息
     */
    public void broadcast(SchedulerMessage message) {
        for (ActorRef<SchedulerMessage> scheduler : schedulers) {
            scheduler.tell(message);
        }
    }
}
