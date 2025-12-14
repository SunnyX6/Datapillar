package com.sunny.job.worker.config;

import com.sunny.job.core.message.ExecutorMessage;
import com.sunny.job.worker.domain.mapper.JobBucketLeaseMapper;
import com.sunny.job.worker.pekko.actor.JobSchedulerContext;
import com.sunny.job.worker.pekko.actor.JobSchedulerManager;
import com.sunny.job.worker.pekko.actor.JobExecutorContext;
import com.sunny.job.worker.pekko.actor.ShardingTaskReceiver;
import com.sunny.job.worker.pekko.ddata.BucketStateManager;
import com.sunny.job.worker.pekko.ddata.JobRunStateManager;
import com.sunny.job.worker.pekko.ddata.ShardStateManager;
import com.sunny.job.worker.pekko.ddata.SplitStateManager;
import com.sunny.job.worker.pekko.ddata.WorkerStateManager;
import com.sunny.job.worker.service.JobPreloadService;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.cluster.typed.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

/**
 * Pekko Cluster 配置
 * <p>
 * 去中心化架构：
 * - 调度本地化：每个 Worker 启动一个本地 JobScheduler，只调度自己 Bucket 的任务
 * - 执行分布式：任务执行可跨 Worker，分片任务广播给所有 Worker 并行执行
 * <p>
 * 分片调度（高性能模式）：
 * - 创建 N 个 JobScheduler Actor（N = CPU 核心数）
 * - 每个 Scheduler 负责 bucketCount/N 个 Bucket
 * - 按 bucketId % N 路由，实现并行调度
 * <p>
 * 核心组件：
 * - JobSchedulerManager: 分片调度管理器，管理多个 JobScheduler
 * - JobScheduler: 本地调度器，负责调度决策
 * - ShardingTaskReceiver: 分片任务接收器，接收来自其他 Worker 的分片任务
 * - BucketStateManager: CRDT 管理 Bucket 所有权
 * - WorkerStateManager: CRDT 同步 Worker 负载信息
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Configuration
public class ClusterConfig {

    private static final Logger log = LoggerFactory.getLogger(ClusterConfig.class);

    @Value("${datapillar.job.worker.bucket-count:1024}")
    private int bucketCount;

    @Value("${datapillar.job.worker.scheduler-shard-count:0}")
    private int schedulerShardCount;

    /**
     * WorkerStateManager Bean
     * <p>
     * 使用 CRDT 在集群中同步 Worker 负载信息
     * 用于路由策略选择目标 Worker（本地化架构下主要用于状态监控）
     */
    @Bean
    @DependsOn("actorSystem")
    public WorkerStateManager workerStateManager(ActorSystem<Void> actorSystem, CacheConfig cacheConfig) {
        log.info("初始化 WorkerStateManager...");
        CacheConfig.WorkerStateCacheConfig config = cacheConfig.getWorkerState();
        return new WorkerStateManager(actorSystem, config.getMaxSize(), config.getExpireAfterWrite());
    }

    /**
     * BucketStateManager Bean
     * <p>
     * 使用 CRDT 管理 Bucket 所有权
     * 同时通过 DB 持久化租约信息，用于 Worker 重启时恢复
     * 核心组件：实现去中心化的任务分片
     * <p>
     * 基于一致性哈希分配 Bucket：
     * - Worker 加入/离开时只迁移 bucketCount/N 个 Bucket
     * - 虚拟节点（160 个）保证分布均匀
     */
    @Bean
    @DependsOn({"actorSystem", "workerStateManager"})
    public BucketStateManager bucketStateManager(ActorSystem<Void> actorSystem,
                                                  WorkerStateManager workerStateManager,
                                                  JobBucketLeaseMapper jobBucketLeaseMapper) {
        log.info("初始化 BucketStateManager，bucketCount={}...", bucketCount);
        return new BucketStateManager(actorSystem, bucketCount, workerStateManager, jobBucketLeaseMapper);
    }

    /**
     * JobRunStateManager Bean
     * <p>
     * 使用 CRDT 缓存任务运行状态，实现读写分离：
     * - 写路径: Worker → DB（持久化）→ CRDT（广播同步）
     * - 读路径: Worker → 本地 CRDT 副本（零网络开销）
     */
    @Bean
    @DependsOn("actorSystem")
    public JobRunStateManager jobRunStateManager(ActorSystem<Void> actorSystem, CacheConfig cacheConfig) {
        log.info("初始化 JobRunStateManager...");
        CacheConfig.JobRunStateCacheConfig config = cacheConfig.getJobRunState();
        return new JobRunStateManager(actorSystem, config.getMaxSize(), config.getExpireAfterWrite());
    }

    /**
     * SplitStateManager Bean
     * <p>
     * 使用 CRDT 管理分片任务状态：
     * - 协调多个 Worker 的分片处理，避免重复
     * - 跟踪每个分片范围的处理状态
     */
    @Bean
    @DependsOn("actorSystem")
    public SplitStateManager splitStateManager(ActorSystem<Void> actorSystem, CacheConfig cacheConfig) {
        log.info("初始化 SplitStateManager...");
        CacheConfig.SplitStateCacheConfig config = cacheConfig.getSplitState();
        // 使用 Pekko Cluster 地址
        String pekkoAddress = Cluster.get(actorSystem).selfMember().address().toString();
        return new SplitStateManager(actorSystem, pekkoAddress, config.getMaxSize(), config.getExpireAfterWrite());
    }

    /**
     * ShardStateManager Bean
     * <p>
     * 使用 CRDT 管理分片任务状态：
     * - 记录每个分片的执行状态
     * - 支持分片结果汇聚
     */
    @Bean
    @DependsOn("actorSystem")
    public ShardStateManager shardStateManager(ActorSystem<Void> actorSystem, CacheConfig cacheConfig) {
        log.info("初始化 ShardStateManager...");
        CacheConfig.ShardStateCacheConfig config = cacheConfig.getShardState();
        return new ShardStateManager(actorSystem, config.getMaxSize(), config.getExpireAfterWrite());
    }

    /**
     * ShardingTaskReceiver Bean
     * <p>
     * 分片任务接收器：
     * - 注册到 Cluster Receptionist，供其他 Worker 发现
     * - 接收来自其他 Worker 的分片任务
     * - spawn 本地 JobExecutor 执行分片任务
     * - 执行完成后发送 ShardCompleted 给调度者
     */
    @Bean
    @DependsOn("actorSystem")
    public ActorRef<ExecutorMessage> shardingTaskReceiverRef(
            ActorSystem<Void> actorSystem,
            JobExecutorContext executorContext) {

        log.info("初始化 ShardingTaskReceiver...");

        ActorRef<ExecutorMessage> receiverRef = actorSystem.systemActorOf(
                ShardingTaskReceiver.create(executorContext),
                "sharding-task-receiver",
                org.apache.pekko.actor.typed.Props.empty()
        );

        log.info("ShardingTaskReceiver 启动完成");
        return receiverRef;
    }

    /**
     * 初始化 JobSchedulerManager（分片调度管理器）
     * <p>
     * 高性能设计：
     * - 创建 N 个 JobScheduler Actor（N = CPU 核心数或配置值）
     * - 每个 Scheduler 负责 bucketCount/N 个 Bucket
     * - 按 bucketId % N 路由，实现并行调度
     * - 吞吐量提升 N 倍
     */
    @Bean
    @DependsOn({"bucketStateManager", "splitStateManager", "jobRunStateManager", "shardingTaskReceiverRef"})
    public JobSchedulerManager jobSchedulerManager(
            ActorSystem<Void> actorSystem,
            JobSchedulerContext schedulerContext,
            JobExecutorContext executorContext,
            BucketStateManager bucketStateManager,
            SplitStateManager splitStateManager,
            JobRunStateManager jobRunStateManager,
            JobPreloadService preloadService) {

        // 计算分片数量：配置值 > 0 则使用配置值，否则使用 CPU 核心数
        int shardCount = schedulerShardCount > 0
                ? schedulerShardCount
                : Runtime.getRuntime().availableProcessors();

        log.info("初始化 JobSchedulerManager，shardCount={}...", shardCount);

        // 使用 Pekko Cluster 地址
        String pekkoAddress = Cluster.get(actorSystem).selfMember().address().toString();

        JobSchedulerManager manager = new JobSchedulerManager(
                actorSystem,
                shardCount,
                schedulerContext,
                executorContext,
                bucketStateManager,
                splitStateManager,
                jobRunStateManager,
                preloadService,
                pekkoAddress
        );

        log.info("JobSchedulerManager 启动完成，共 {} 个并行 Scheduler", shardCount);
        return manager;
    }
}
