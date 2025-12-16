package com.sunny.job.worker.config;

import com.sunny.job.core.id.IdGenerator;
import com.sunny.job.core.message.ExecutorMessage;
import com.sunny.job.worker.domain.mapper.JobBucketLeaseMapper;
import com.sunny.job.worker.pekko.actor.JobSchedulerContext;
import com.sunny.job.worker.pekko.actor.JobSchedulerManager;
import com.sunny.job.worker.pekko.actor.JobExecutorContext;
import com.sunny.job.worker.pekko.actor.ShardingTaskReceiver;
import com.sunny.job.worker.pekko.ddata.BucketManager;
import com.sunny.job.worker.pekko.ddata.JobRunLocalCache;
import com.sunny.job.worker.pekko.ddata.MaxJobRunIdState;
import com.sunny.job.worker.pekko.ddata.SplitLocalCache;
import com.sunny.job.worker.pekko.ddata.WorkerManager;
import com.sunny.job.worker.pekko.ddata.JobRunBroadcastState;
import com.sunny.job.worker.pekko.ddata.WorkflowBroadcastState;
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
 * - BucketManager: CRDT 管理 Bucket 所有权
 * - WorkerManager: CRDT 同步 Worker 负载信息
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
     * WorkerManager Bean
     * <p>
     * 使用 Pekko Cluster 成员列表获取存活 Worker
     * 本地缓存负载信息，不使用 CRDT（避免序列化问题）
     */
    @Bean
    @DependsOn("actorSystem")
    public WorkerManager workerManager(ActorSystem<Void> actorSystem) {
        log.info("初始化 WorkerManager...");
        return new WorkerManager(actorSystem);
    }

    /**
     * BucketManager Bean
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
    @DependsOn({"actorSystem", "workerManager"})
    public BucketManager bucketManager(ActorSystem<Void> actorSystem,
                                                  WorkerManager workerManager,
                                                  JobBucketLeaseMapper jobBucketLeaseMapper) {
        log.info("初始化 BucketManager，bucketCount={}...", bucketCount);
        return new BucketManager(actorSystem, bucketCount, workerManager, jobBucketLeaseMapper);
    }

    /**
     * JobRunLocalCache Bean
     * <p>
     * 使用本地缓存管理任务运行状态
     * 不使用 CRDT（避免序列化问题，任务状态已在 DB 持久化）
     */
    @Bean
    public JobRunLocalCache jobRunLocalCache(CacheConfig cacheConfig) {
        log.info("初始化 JobRunLocalCache...");
        CacheConfig.JobRunLocalCacheConfig config = cacheConfig.getJobRunState();
        return new JobRunLocalCache(config.getMaxSize(), config.getExpireAfterWrite());
    }

    /**
     * SplitLocalCache Bean
     * <p>
     * 使用本地缓存管理分片任务状态
     * 不使用 CRDT（每个 Worker 独占自己 Bucket 的任务）
     */
    @Bean
    @DependsOn("actorSystem")
    public SplitLocalCache splitLocalCache(ActorSystem<Void> actorSystem, CacheConfig cacheConfig) {
        log.info("初始化 SplitLocalCache...");
        CacheConfig.SplitLocalCacheConfig config = cacheConfig.getSplitState();
        String pekkoAddress = Cluster.get(actorSystem).selfMember().address().toString();
        return new SplitLocalCache(pekkoAddress, config.getMaxSize(), config.getExpireAfterWrite());
    }

    /**
     * MaxJobRunIdState Bean
     * <p>
     * 使用 CRDT 在集群中同步全局最大 jobRunId
     * 用于事件驱动的增量任务加载：
     * - Worker 加载新任务后，更新 CRDT 中的 maxJobRunId
     * - 其他 Worker 检测到变化，触发增量加载
     */
    @Bean
    @DependsOn("actorSystem")
    public MaxJobRunIdState maxJobRunIdManager(ActorSystem<Void> actorSystem) {
        log.info("初始化 MaxJobRunIdState...");
        return new MaxJobRunIdState(actorSystem);
    }

    /**
     * 分布式 ID 生成器
     * <p>
     * 基于 Pekko Cluster 地址生成节点 ID
     * 用于生成 workflow_run.id 和 job_run.id
     */
    @Bean
    @DependsOn("actorSystem")
    public IdGenerator idGenerator(ActorSystem<Void> actorSystem) {
        String address = Cluster.get(actorSystem).selfMember().address().toString();
        IdGenerator generator = IdGenerator.fromAddress(address);
        log.info("初始化 IdGenerator: nodeId={}, address={}", generator.getNodeId(), address);
        return generator;
    }

    /**
     * 工作流广播监听器
     * <p>
     * 监听 Server 通过 CRDT 广播的工作流事件
     * 收到事件后触发 run 创建逻辑
     */
    @Bean
    @DependsOn("actorSystem")
    public WorkflowBroadcastState workflowBroadcastListener(ActorSystem<Void> actorSystem) {
        log.info("初始化 WorkflowBroadcastState...");
        return new WorkflowBroadcastState(actorSystem);
    }

    /**
     * 任务级广播监听器
     * <p>
     * 监听 Server 通过 CRDT 广播的任务级事件
     * 支持手动执行、重试、终止等操作
     */
    @Bean
    @DependsOn("actorSystem")
    public JobRunBroadcastState jobRunBroadcastState(ActorSystem<Void> actorSystem) {
        log.info("初始化 JobRunBroadcastState...");
        return new JobRunBroadcastState(actorSystem);
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
    @DependsOn({"bucketManager", "splitLocalCache", "jobRunLocalCache", "maxJobRunIdManager", "shardingTaskReceiverRef"})
    public JobSchedulerManager jobSchedulerManager(
            ActorSystem<Void> actorSystem,
            JobSchedulerContext schedulerContext,
            JobExecutorContext executorContext,
            BucketManager bucketManager,
            SplitLocalCache splitLocalCache,
            JobRunLocalCache jobRunLocalCache,
            MaxJobRunIdState maxJobRunIdManager,
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
                bucketManager,
                splitLocalCache,
                jobRunLocalCache,
                maxJobRunIdManager,
                preloadService,
                pekkoAddress
        );

        log.info("JobSchedulerManager 启动完成，共 {} 个并行 Scheduler", shardCount);
        return manager;
    }
}
