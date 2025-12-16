package com.sunny.job.worker.pekko.actor;

import com.sunny.job.core.enums.BlockStrategy;
import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.enums.RouteStrategy;
import com.sunny.job.core.message.SchedulerMessage;
import com.sunny.job.core.message.SchedulerMessage.*;
import com.sunny.job.core.message.ExecutorMessage;
import com.sunny.job.core.message.JobRunInfo;
import com.sunny.job.core.split.DefaultSplitCalculator;
import com.sunny.job.core.split.SplitCalculator;
import com.sunny.job.worker.pekko.ddata.BucketLease;
import com.sunny.job.worker.pekko.ddata.BucketManager;
import com.sunny.job.worker.pekko.ddata.JobRunLocalCache;
import com.sunny.job.worker.pekko.ddata.MaxJobRunIdState;
import com.sunny.job.worker.pekko.ddata.SplitLocalCache;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.DispatcherSelector;
import org.apache.pekko.actor.typed.PostStop;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务调度器（去中心化，每个 Worker 一个本地 Actor）
 * <p>
 * 核心设计原则：调度本地化，执行分布式
 * <p>
 * 异步 DB 查询：
 * - 所有 DB 查询通过 CompletableFuture 异步执行
 * - 查询结果通过消息投递回 Actor（pipeToSelf 模式）
 * - PriorityQueue 只在 Actor 消息处理线程中访问，保证线程安全
 * <p>
 * 调度本地化：
 * - 每个 Worker 有一个本地 JobScheduler
 * - 只调度自己 Bucket 的任务
 * - 调度决策不依赖中心节点
 * <p>
 * 执行分布式：
 * - 普通任务：根据路由策略选择一个 Worker 执行
 * - 分片任务（SHARDING）：Worker 自治模式，自己拆分、自己标记、自己执行
 *   - Worker 根据自身能力计算 splitSize
 *   - Worker 从 CRDT 获取下一个未处理范围，标记后执行
 *   - 通过 CRDT 协调，避免重复处理
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public class JobScheduler extends AbstractBehavior<SchedulerMessage> {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    /**
     * 加载来源常量
     */
    private static final String SOURCE_INIT = "init";
    private static final String SOURCE_BUCKET = "bucket";
    private static final String SOURCE_INCREMENTAL = "incremental";
    private static final String SOURCE_RERUN = "rerun";

    private final JobSchedulerContext schedulerContext;
    private final JobExecutorContext executorContext;
    private final BucketManager bucketManager;
    private final SplitLocalCache splitLocalCache;
    private final JobRunLocalCache jobRunLocalCache;
    private final MaxJobRunIdState maxJobRunIdManager;
    private final SplitCalculator splitCalculator;
    private final String selfAddress;

    /**
     * 分片 ID（0 ~ shardCount-1）
     */
    private final int shardId;

    /**
     * 总分片数
     */
    private final int shardCount;

    /**
     * 最大待调度任务数（内存保护）
     */
    private final int maxPendingTasks;

    /**
     * 执行器 ID 生成器
     */
    private final AtomicLong executorIdGenerator = new AtomicLong(0);

    /**
     * 当前 Worker 持有的 Bucket
     */
    private final Set<Integer> myBuckets = new HashSet<>();

    /**
     * job_run_id → JobRunInfo 映射
     */
    private final Map<Long, JobRunInfo> jobRunMap = new HashMap<>();

    /**
     * bucketId → Set<jobRunId> 反向索引（用于 Bucket 丢失时快速清理）
     */
    private final Map<Integer, Set<Long>> bucketJobIndex = new HashMap<>();

    /**
     * workflowId → Set<jobRunId> 反向索引（用于工作流下线时快速取消）
     */
    private final Map<Long, Set<Long>> workflowJobIndex = new HashMap<>();

    /**
     * parent_run_id → Set<child_run_id> 反向索引（用于快速找下游任务）
     */
    private final Map<Long, Set<Long>> downstreamIndex = new HashMap<>();

    /**
     * jobId → Set<jobRunId> 正在执行的任务索引（用于阻塞策略检查）
     */
    private final Map<Long, Set<Long>> runningJobIndex = new HashMap<>();

    /**
     * jobRunId → ActorRef 正在执行的执行器引用（用于取消任务）
     */
    private final Map<Long, ActorRef<ExecutorMessage>> runningExecutors = new HashMap<>();

    /**
     * 按 triggerTime 排序的时间槽队列
     * <p>
     * 优化：使用 TimeSlotQueue 替代 PriorityQueue
     * - remove 操作从 O(n) 优化为 O(1)
     * - 线程安全保证：只在 Actor 消息处理线程中访问
     */
    private final TimeSlotQueue triggerQueue = new TimeSlotQueue();

    /**
     * 时间轮定时器
     */
    private final HashedWheelTimer timer;

    /**
     * 当前定时器任务
     */
    private Timeout currentTimeout;

    /**
     * Bucket 续租定时器
     */
    private Timeout renewalTimeout;

    /**
     * 上次扫描的最大 ID
     */
    private long lastMaxId = 0;

    /**
     * 正在执行的分片任务
     * <p>
     * jobRunId → 正在执行的分片数量
     */
    private final Map<Long, Integer> runningShardingJobs = new ConcurrentHashMap<>();

    public static Behavior<SchedulerMessage> create(JobSchedulerContext schedulerContext,
                                                     JobExecutorContext executorContext,
                                                     BucketManager bucketManager,
                                                     SplitLocalCache splitLocalCache,
                                                     JobRunLocalCache jobRunLocalCache,
                                                     MaxJobRunIdState maxJobRunIdManager,
                                                     String selfAddress) {
        // 单 Scheduler 模式（兼容旧接口）
        return create(schedulerContext, executorContext, bucketManager,
                splitLocalCache, jobRunLocalCache, maxJobRunIdManager, selfAddress, 0, 1);
    }

    /**
     * 创建分片调度器
     *
     * @param shardId    分片 ID（0 ~ shardCount-1）
     * @param shardCount 总分片数
     */
    public static Behavior<SchedulerMessage> create(JobSchedulerContext schedulerContext,
                                                     JobExecutorContext executorContext,
                                                     BucketManager bucketManager,
                                                     SplitLocalCache splitLocalCache,
                                                     JobRunLocalCache jobRunLocalCache,
                                                     MaxJobRunIdState maxJobRunIdManager,
                                                     String selfAddress,
                                                     int shardId,
                                                     int shardCount) {
        return Behaviors.setup(ctx -> new JobScheduler(ctx, schedulerContext, executorContext,
                bucketManager, splitLocalCache, jobRunLocalCache, maxJobRunIdManager,
                selfAddress, shardId, shardCount));
    }

    private JobScheduler(ActorContext<SchedulerMessage> context,
                         JobSchedulerContext schedulerContext,
                         JobExecutorContext executorContext,
                         BucketManager bucketManager,
                         SplitLocalCache splitLocalCache,
                         JobRunLocalCache jobRunLocalCache,
                         MaxJobRunIdState maxJobRunIdManager,
                         String selfAddress,
                         int shardId,
                         int shardCount) {
        super(context);
        this.schedulerContext = schedulerContext;
        this.executorContext = executorContext;
        this.bucketManager = bucketManager;
        this.splitLocalCache = splitLocalCache;
        this.jobRunLocalCache = jobRunLocalCache;
        this.maxJobRunIdManager = maxJobRunIdManager;
        this.splitCalculator = new DefaultSplitCalculator();
        this.selfAddress = selfAddress;
        this.shardId = shardId;
        this.shardCount = shardCount;
        this.maxPendingTasks = schedulerContext.getMaxPendingTasks();
        this.timer = new HashedWheelTimer(100, TimeUnit.MILLISECONDS, 512);

        log.info("JobScheduler-{} 启动, selfAddress={}, 负责 Bucket % {} == {}, maxPendingTasks={}",
                shardId, selfAddress, shardCount, shardId, maxPendingTasks);
        context.getSelf().tell(new StartScan());
    }

    @Override
    public Receive<SchedulerMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(StartScan.class, this::onStartScan)
                .onMessage(RegisterJob.class, this::onRegisterJob)
                .onMessage(JobCompleted.class, this::onJobCompleted)
                .onMessage(TimerFired.class, this::onTimerFired)
                .onMessage(WorkflowCompleted.class, this::onWorkflowCompleted)
                .onMessage(BucketAcquired.class, this::onBucketAcquired)
                .onMessage(BucketLost.class, this::onBucketLost)
                .onMessage(RenewBuckets.class, this::onRenewBuckets)
                .onMessage(SplitCompleted.class, this::onSplitCompleted)
                .onMessage(JobsLoaded.class, this::onJobsLoaded)
                .onMessage(JobsLoadFailed.class, this::onJobsLoadFailed)
                .onMessage(RetrySplit.class, this::onRetrySplit)
                .onMessage(GlobalMaxIdChanged.class, this::onGlobalMaxIdChanged)
                .onMessage(NewJobsCreated.class, this::onNewJobsCreated)
                .onMessage(CancelWorkflow.class, this::onCancelWorkflow)
                .onMessage(SchedulerMessage.RefreshJobInfo.class, this::onRefreshJobInfo)
                .onSignal(PostStop.class, this::onPostStop)
                .build();
    }

    /**
     * 处理 Actor 停止信号
     * <p>
     * 释放资源：关闭 HashedWheelTimer，取消所有定时器
     */
    private Behavior<SchedulerMessage> onPostStop(PostStop signal) {
        log.info("JobScheduler-{} 停止，释放资源...", shardId);

        // 关闭时间轮定时器
        if (timer != null) {
            timer.stop();
            log.info("HashedWheelTimer 已关闭");
        }

        // 取消当前定时任务
        if (currentTimeout != null) {
            currentTimeout.cancel();
        }

        // 取消续租定时任务
        if (renewalTimeout != null) {
            renewalTimeout.cancel();
        }

        log.info("JobScheduler-{} 资源释放完成", shardId);
        return this;
    }

    /**
     * 处理异步加载完成
     * <p>
     * 所有 DB 查询结果都通过此消息处理，保证 PriorityQueue 线程安全
     */
    private Behavior<SchedulerMessage> onJobsLoaded(JobsLoaded msg) {
        log.info("任务加载完成: source={}, count={}, newMaxId={}",
                msg.source(), msg.jobs().size(), msg.newMaxId());

        // 加载任务到内存
        for (JobRunInfo job : msg.jobs()) {
            loadJobRun(job);
        }

        // 更新 lastMaxId
        if (msg.newMaxId() > lastMaxId) {
            lastMaxId = msg.newMaxId();

            // 更新 CRDT 中的 maxJobRunId，通知其他 Worker
            maxJobRunIdManager.updateMaxId(lastMaxId);
        }

        // 根据来源执行后续操作
        switch (msg.source()) {
            case SOURCE_INIT -> {
                // 初始化加载完成，启动 Bucket 续租定时器
                scheduleBucketRenewal();
                scheduleNextTrigger();
                log.info("调度器初始化完成，持有 {} 个 Bucket，加载 {} 个任务",
                        myBuckets.size(), jobRunMap.size());
            }
            case SOURCE_BUCKET, SOURCE_INCREMENTAL, SOURCE_RERUN -> {
                // Bucket 加载、增量加载、重跑检测完成
                scheduleNextTrigger();
            }
        }

        return this;
    }

    /**
     * 处理异步加载失败
     */
    private Behavior<SchedulerMessage> onJobsLoadFailed(JobsLoadFailed msg) {
        log.error("任务加载失败: source={}, reason={}", msg.source(), msg.reason());

        // 如果是初始化加载失败，仍然启动续租和定时器
        if (SOURCE_INIT.equals(msg.source())) {
            scheduleBucketRenewal();
            scheduleNextTrigger();
            log.warn("初始化加载失败，调度器以空任务启动");
        }

        return this;
    }

    /**
     * 处理全局 maxJobRunId 变化（事件驱动增量加载）
     * <p>
     * 来源：MaxJobRunIdState 检测到 CRDT 中全局 maxJobRunId 变化
     * 触发增量加载自己 Bucket 的新任务
     */
    private Behavior<SchedulerMessage> onGlobalMaxIdChanged(GlobalMaxIdChanged msg) {
        long newMaxId = msg.newMaxId();

        // 如果新的全局最大 ID 大于本地已知的最大 ID，说明有新任务创建
        if (newMaxId > lastMaxId && !myBuckets.isEmpty()) {
            log.info("JobScheduler-{} 收到全局 maxJobRunId 变化通知: {} -> {}，触发增量加载",
                    shardId, lastMaxId, newMaxId);

            // 异步加载新任务
            var self = getContext().getSelf();
            schedulerContext.loadNewJobsByBucketsAsync(lastMaxId, myBuckets)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            self.tell(new JobsLoadFailed(error.getMessage(), SOURCE_INCREMENTAL));
                        } else if (!result.jobs().isEmpty()) {
                            self.tell(new JobsLoaded(result.jobs(), result.newMaxId(), SOURCE_INCREMENTAL));
                        } else if (result.newMaxId() > lastMaxId) {
                            // 即使没有新任务（可能不属于自己的 Bucket），也更新 lastMaxId
                            lastMaxId = result.newMaxId();
                        }
                    });
        }

        return this;
    }

    /**
     * 处理新任务创建（工作流完成后创建下一批任务）
     * <p>
     * 流程：
     * 1. 过滤属于自己 Bucket 的任务
     * 2. 加载到内存（jobRunMap + triggerQueue）
     * 3. 异步写入 DB 持久化
     * 4. 更新 CRDT maxJobRunId 通知其他 Worker
     */
    private Behavior<SchedulerMessage> onNewJobsCreated(NewJobsCreated msg) {
        log.info("收到新任务创建通知: workflowRunId={}, count={}",
                msg.workflowRunId(), msg.jobs().size());

        // 1. 过滤属于自己 Bucket 的任务
        List<JobRunInfo> myJobs = new ArrayList<>();
        long maxId = lastMaxId;

        for (JobRunInfo job : msg.jobs()) {
            // 检查是否属于自己的 Bucket
            if (myBuckets.contains(job.getBucketId()) && isMyBucket(job.getBucketId())) {
                myJobs.add(job);
            }
            // 更新最大 ID
            if (job.getJobRunId() > maxId) {
                maxId = job.getJobRunId();
            }
        }

        if (myJobs.isEmpty()) {
            log.debug("没有属于自己 Bucket 的新任务");
            // 仍然需要更新 CRDT 通知其他 Worker
            if (maxId > lastMaxId) {
                lastMaxId = maxId;
                maxJobRunIdManager.updateMaxId(lastMaxId);
            }
            return this;
        }

        log.info("加载 {} 个属于自己 Bucket 的新任务", myJobs.size());

        // 2. 加载到内存
        for (JobRunInfo job : myJobs) {
            loadJobRun(job);
        }

        // 3. 异步写入 DB 持久化
        final long finalMaxId = maxId;
        schedulerContext.persistJobRunsAsync(myJobs)
                .whenComplete((ids, error) -> {
                    if (error != null) {
                        log.error("异步持久化新任务失败: {}", error.getMessage());
                    } else {
                        log.debug("异步持久化 {} 个新任务成功", ids.size());
                    }
                });

        // 4. 更新 lastMaxId 和 CRDT
        if (finalMaxId > lastMaxId) {
            lastMaxId = finalMaxId;
            maxJobRunIdManager.updateMaxId(lastMaxId);
        }

        // 5. 重新调度定时器
        scheduleNextTrigger();

        return this;
    }

    /**
     * 处理分片范围完成报告
     * <p>
     * Worker 执行完一个分片范围后，继续拆分执行下一个范围
     */
    private Behavior<SchedulerMessage> onSplitCompleted(SplitCompleted msg) {
        log.info("收到分片完成报告: jobRunId={}, range=[{}, {}), status={}, worker={}",
                msg.jobRunId(), msg.splitStart(), msg.splitEnd(), msg.status(), msg.workerAddress());

        JobStatus status = JobStatus.of(msg.status());

        // 更新 CRDT 状态
        if (status.isSuccess()) {
            splitLocalCache.markCompleted(msg.jobRunId(), msg.splitStart(), msg.splitEnd(), msg.message());
        } else {
            splitLocalCache.markFailed(msg.jobRunId(), msg.splitStart(), msg.splitEnd(), msg.message());
        }

        // 减少正在执行的分片计数
        Integer runningCount = runningShardingJobs.get(msg.jobRunId());
        if (runningCount != null && runningCount > 0) {
            runningShardingJobs.put(msg.jobRunId(), runningCount - 1);
        }

        // 如果是本 Worker 执行的分片，继续拆分下一个
        if (selfAddress.equals(msg.workerAddress())) {
            JobRunInfo jobRun = jobRunMap.get(msg.jobRunId());
            if (jobRun != null && jobRun.getRouteStrategy() == RouteStrategy.SHARDING) {
                // 继续拆分执行下一个范围
                executeNextSplit(jobRun);
            }
        }

        return this;
    }

    /**
     * 处理启动扫描
     * <p>
     * 分片模式：
     * - 如果 shardCount > 1，由 JobSchedulerManager 统一订阅并路由 Bucket 事件
     * - 每个 Scheduler 只认领 bucketId % shardCount == shardId 的 Bucket
     * <p>
     * 单 Scheduler 模式：
     * - 自己订阅 Bucket 变化
     * - 认领所有可用 Bucket
     */
    private Behavior<SchedulerMessage> onStartScan(StartScan msg) {
        log.info("JobScheduler-{} 开始初始化...", shardId);

        var self = getContext().getSelf();

        // 单 Scheduler 模式：自己订阅 Bucket 变化
        // 分片模式：由 JobSchedulerManager 统一订阅并路由
        if (shardCount == 1) {
            bucketManager.subscribe(
                    bucketId -> self.tell(new BucketAcquired(bucketId)),
                    bucketId -> self.tell(new BucketLost(bucketId))
            );
        }

        // 从 DB 恢复之前持有的 Bucket，只认领属于自己 shard 的
        List<Integer> previousBuckets = bucketManager.recoverFromDb();
        Set<Integer> recoveredBuckets = new HashSet<>();
        for (Integer bucketId : previousBuckets) {
            if (isMyBucket(bucketId) && bucketManager.tryAcquireBucket(bucketId)) {
                recoveredBuckets.add(bucketId);
            }
        }
        if (!recoveredBuckets.isEmpty()) {
            log.info("JobScheduler-{} 从 DB 恢复并认领 {} 个 Bucket", shardId, recoveredBuckets.size());
        }

        // 认领其他空闲 Bucket，只认领属于自己 shard 的
        Set<Integer> newBuckets = bucketManager.acquireAvailableBucketsForShard(shardId, shardCount);
        newBuckets.removeAll(recoveredBuckets);
        if (!newBuckets.isEmpty()) {
            log.info("JobScheduler-{} 认领 {} 个新 Bucket", shardId, newBuckets.size());
        }

        // 合并所有认领的 Bucket
        myBuckets.addAll(recoveredBuckets);
        myBuckets.addAll(newBuckets);
        log.info("JobScheduler-{} 共持有 {} 个 Bucket", shardId, myBuckets.size());

        // 异步加载自己 Bucket 的任务
        if (!myBuckets.isEmpty()) {
            schedulerContext.loadWaitingJobsByBucketsAsync(myBuckets)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            self.tell(new JobsLoadFailed(error.getMessage(), SOURCE_INIT));
                        } else {
                            self.tell(new JobsLoaded(result.jobs(), result.newMaxId(), SOURCE_INIT));
                        }
                    });
        } else {
            // 没有 Bucket，直接完成初始化
            scheduleBucketRenewal();
            scheduleNextTrigger();
            log.info("JobScheduler-{} 初始化完成，无 Bucket", shardId);
        }

        return this;
    }

    /**
     * 判断 Bucket 是否属于当前 shard
     */
    private boolean isMyBucket(int bucketId) {
        return bucketId % shardCount == shardId;
    }

    /**
     * 处理 Bucket 获得事件
     */
    private Behavior<SchedulerMessage> onBucketAcquired(BucketAcquired msg) {
        int bucketId = msg.bucketId();

        // 检查是否属于当前 shard
        if (!isMyBucket(bucketId)) {
            log.debug("JobScheduler-{} 忽略不属于自己的 Bucket: {}", shardId, bucketId);
            return this;
        }

        if (myBuckets.contains(bucketId)) {
            return this;
        }

        log.info("JobScheduler-{} 获得新 Bucket: {}", shardId, bucketId);
        myBuckets.add(bucketId);

        // 异步加载该 Bucket 的任务
        var self = getContext().getSelf();
        schedulerContext.loadWaitingJobsByBucketAsync(bucketId)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        self.tell(new JobsLoadFailed(error.getMessage(), SOURCE_BUCKET));
                    } else {
                        self.tell(new JobsLoaded(result.jobs(), result.newMaxId(), SOURCE_BUCKET));
                    }
                });

        return this;
    }

    /**
     * 处理 Bucket 丢失事件
     */
    private Behavior<SchedulerMessage> onBucketLost(BucketLost msg) {
        int bucketId = msg.bucketId();
        if (!myBuckets.contains(bucketId)) {
            return this;
        }

        log.info("丢失 Bucket: {}", bucketId);
        myBuckets.remove(bucketId);

        // 清理该 Bucket 的任务
        cleanupBucketTasks(bucketId);

        return this;
    }

    /**
     * 清理指定 Bucket 的任务
     */
    private void cleanupBucketTasks(int bucketId) {
        Set<Long> jobRunIds = bucketJobIndex.remove(bucketId);
        if (jobRunIds == null || jobRunIds.isEmpty()) {
            return;
        }

        log.info("清理 Bucket {} 的 {} 个任务", bucketId, jobRunIds.size());

        for (Long jobRunId : jobRunIds) {
            JobRunInfo job = jobRunMap.remove(jobRunId);
            if (job != null) {
                // 从触发队列移除
                triggerQueue.remove(job);

                // 从下游索引移除
                if (job.hasDependencies()) {
                    for (Long parentId : job.getParentJobRunIds()) {
                        Set<Long> children = downstreamIndex.get(parentId);
                        if (children != null) {
                            children.remove(jobRunId);
                        }
                    }
                }

                // 从运行中索引移除
                Set<Long> running = runningJobIndex.get(job.getJobId());
                if (running != null) {
                    running.remove(jobRunId);
                }

                // 从执行器引用中移除
                runningExecutors.remove(jobRunId);
            }
        }
    }

    /**
     * 处理 Bucket 续租
     */
    private Behavior<SchedulerMessage> onRenewBuckets(RenewBuckets msg) {
        bucketManager.renewAllBuckets();
        scheduleBucketRenewal();
        return this;
    }

    /**
     * 启动 Bucket 续租定时器
     */
    private void scheduleBucketRenewal() {
        var self = getContext().getSelf();
        renewalTimeout = timer.newTimeout(timeout -> {
            self.tell(new RenewBuckets());
        }, BucketLease.RENEWAL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 处理任务注册
     */
    private Behavior<SchedulerMessage> onRegisterJob(RegisterJob msg) {
        log.debug("注册任务: jobRunId={}, jobId={}, triggerTime={}", msg.jobRunId(), msg.jobId(), msg.triggerTime());

        JobRunInfo jobRun = jobRunMap.get(msg.jobRunId());
        if (jobRun == null) {
            // 任务不在内存中，使用消息携带的 JobRunInfo
            if (msg.jobRunInfo() != null) {
                jobRun = msg.jobRunInfo();
                // 使用 loadJobRun 来正确构建所有索引（包括下游依赖索引）
                loadJobRun(jobRun);
                log.debug("从消息添加任务到内存: jobRunId={}, hasDependencies={}",
                        msg.jobRunId(), jobRun.hasDependencies());
            } else {
                log.warn("任务不在内存中且消息未携带 JobRunInfo，跳过: jobRunId={}", msg.jobRunId());
                return this;
            }
        }

        // 补全 job_params 等字段（如果消息已携带则无需再次补全）
        if (msg.jobRunInfo() == null) {
            schedulerContext.enrichJobInfo(jobRun);
        }

        // 调度下一次触发（loadJobRun 已经添加到 triggerQueue，这里确保调度器启动）
        scheduleNextTrigger();

        return this;
    }

    /**
     * 处理任务完成
     */
    private Behavior<SchedulerMessage> onJobCompleted(JobCompleted msg) {
        log.info("任务完成: jobRunId={}, status={}", msg.jobRunId(), msg.status());

        JobRunInfo jobRun = jobRunMap.get(msg.jobRunId());
        if (jobRun == null) {
            log.warn("任务不存在: jobRunId={}", msg.jobRunId());
            return this;
        }

        // 更新状态
        JobStatus status = JobStatus.of(msg.status());
        jobRun.setStatus(status);

        // 从正在执行的任务索引中移除
        Set<Long> runningJobRunIds = runningJobIndex.get(jobRun.getJobId());
        if (runningJobRunIds != null) {
            runningJobRunIds.remove(msg.jobRunId());
            if (runningJobRunIds.isEmpty()) {
                runningJobIndex.remove(jobRun.getJobId());
            }
        }

        // 从执行器引用中移除
        runningExecutors.remove(msg.jobRunId());

        // 检查下游依赖（仅成功时触发下游，且只触发同 Bucket 内的任务）
        if (status.isSuccess()) {
            checkAndDispatchDownstream(msg.jobRunId());
        }

        // 内存清理：任务终态后清理相关数据结构
        if (status.isTerminal()) {
            cleanupCompletedJob(msg.jobRunId(), jobRun);
        }

        return this;
    }

    /**
     * 清理已完成任务的内存数据
     *
     * @param jobRunId 任务 ID
     * @param jobRun   任务信息
     */
    private void cleanupCompletedJob(long jobRunId, JobRunInfo jobRun) {
        // 1. 从 jobRunMap 移除
        jobRunMap.remove(jobRunId);

        // 2. 从 bucketJobIndex 移除
        Set<Long> bucketJobs = bucketJobIndex.get(jobRun.getBucketId());
        if (bucketJobs != null) {
            bucketJobs.remove(jobRunId);
        }

        // 3. 从 workflowJobIndex 移除
        Set<Long> workflowJobs = workflowJobIndex.get(jobRun.getWorkflowId());
        if (workflowJobs != null) {
            workflowJobs.remove(jobRunId);
        }

        // 4. 从 downstreamIndex 移除（作为父任务的索引）
        downstreamIndex.remove(jobRunId);

        // 5. 从 runningShardingJobs 移除（如果计数为 0）
        Integer shardingCount = runningShardingJobs.get(jobRunId);
        if (shardingCount != null && shardingCount <= 0) {
            runningShardingJobs.remove(jobRunId);
        }

        log.debug("已清理完成任务内存数据: jobRunId={}", jobRunId);
    }

    /**
     * 处理定时器触发
     */
    private Behavior<SchedulerMessage> onTimerFired(TimerFired msg) {
        long now = System.currentTimeMillis();
        log.debug("定时器触发: now={}", now);

        // 分发所有到期任务
        while (!triggerQueue.isEmpty()) {
            JobRunInfo jobRun = triggerQueue.peek();
            if (jobRun.getTriggerTime() > now) {
                break;
            }

            triggerQueue.poll();

            // 跳过非 WAITING 状态的任务
            if (jobRun.getStatus() != JobStatus.WAITING) {
                continue;
            }

            // 检查依赖是否满足
            if (checkDependenciesSatisfied(jobRun)) {
                dispatchJob(jobRun);
            } else {
                log.debug("任务依赖未满足，等待: jobRunId={}", jobRun.getJobRunId());
            }
        }

        // 异步检测被重跑的任务
        detectAndReloadRerunJobs();

        // 设置下一个定时器
        scheduleNextTrigger();

        return this;
    }

    /**
     * 异步检测被重跑的任务
     */
    private void detectAndReloadRerunJobs() {
        // 收集内存中失败状态的任务 ID
        List<Long> failedJobRunIds = new ArrayList<>();
        for (Map.Entry<Long, JobRunInfo> entry : jobRunMap.entrySet()) {
            JobStatus status = entry.getValue().getStatus();
            if (status == JobStatus.FAIL || status == JobStatus.TIMEOUT) {
                failedJobRunIds.add(entry.getKey());
            }
        }

        if (failedJobRunIds.isEmpty()) {
            return;
        }

        // 异步检测被重跑的任务
        var self = getContext().getSelf();
        schedulerContext.detectRerunJobsAsync(failedJobRunIds)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.warn("检测重跑任务失败: {}", error.getMessage());
                    } else if (!result.jobs().isEmpty()) {
                        // 更新内存中的任务状态为 WAITING
                        for (JobRunInfo job : result.jobs()) {
                            job.setStatus(JobStatus.WAITING);
                        }
                        self.tell(new JobsLoaded(result.jobs(), 0L, SOURCE_RERUN));
                    }
                });
    }

    /**
     * 处理工作流完成
     */
    private Behavior<SchedulerMessage> onWorkflowCompleted(WorkflowCompleted msg) {
        log.info("工作流完成: workflowRunId={}, status={}", msg.workflowRunId(), msg.status());
        return this;
    }

    /**
     * 检查依赖是否满足（所有 parent.status = SUCCESS）
     * <p>
     * 读写分离优化：
     * - 本地内存中的父任务：直接从 jobRunMap 读取
     * - 跨 Bucket 的父任务：从 CRDT 缓存读取（零网络开销）
     */
    private boolean checkDependenciesSatisfied(JobRunInfo jobRun) {
        if (!jobRun.hasDependencies()) {
            return true;
        }

        for (Long parentRunId : jobRun.getParentJobRunIds()) {
            // 1. 优先从本地内存读取（同 Bucket 的任务）
            JobRunInfo parent = jobRunMap.get(parentRunId);
            if (parent != null) {
                if (!parent.getStatus().isSuccess()) {
                    return false;
                }
                continue;
            }

            // 2. 本地内存没有，从 CRDT 缓存读取（跨 Bucket 的任务，零网络开销）
            JobStatus status = jobRunLocalCache.getStatus(parentRunId);
            if (status == null || !status.isSuccess()) {
                return false;
            }
        }

        return true;
    }

    /**
     * 检查并分发下游任务（仅同 Bucket 内）
     */
    private void checkAndDispatchDownstream(long completedJobRunId) {
        Set<Long> children = downstreamIndex.get(completedJobRunId);
        if (children == null || children.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        for (Long childRunId : children) {
            JobRunInfo child = jobRunMap.get(childRunId);
            if (child != null && child.getStatus() == JobStatus.WAITING) {
                if (checkDependenciesSatisfied(child)) {
                    // 检查 triggerTime 是否到达
                    // triggerTime = 0 表示依赖满足即可执行
                    // triggerTime > 0 表示需要等待时间到达
                    if (child.getTriggerTime() == 0 || child.getTriggerTime() <= now) {
                        dispatchJob(child);
                    } else {
                        // 依赖已满足但时间未到，重新加入触发队列等待
                        log.debug("依赖已满足但 triggerTime 未到，等待触发: jobRunId={}, triggerTime={}",
                                child.getJobRunId(), child.getTriggerTime());
                        triggerQueue.add(child);
                        scheduleNextTrigger();
                    }
                }
            }
        }
    }

    /**
     * 分发任务（本地执行）
     */
    private void dispatchJob(JobRunInfo jobRun) {
        log.info("分发任务: jobRunId={}, jobName={}, blockStrategy={}",
                jobRun.getJobRunId(), jobRun.getJobName(), jobRun.getBlockStrategy());

        // 检查阻塞策略
        if (!checkBlockStrategy(jobRun)) {
            log.info("任务被阻塞策略丢弃: jobRunId={}, blockStrategy={}",
                    jobRun.getJobRunId(), jobRun.getBlockStrategy());
            return;
        }

        // 容量检查：如果本地容量不足，尝试转发或延迟执行
        if (!executorContext.hasCapacity()) {
            // 尝试查找其他有容量的 Worker
            JobSchedulerContext.WorkerCapacity targetWorker = findAvailableWorker();
            if (targetWorker != null && !targetWorker.address().equals(selfAddress)) {
                // 找到其他 Worker，转发任务
                log.info("本地容量不足(running={}/{}), 转发任务到其他 Worker: jobRunId={}, targetWorker={}",
                        executorContext.getRunningTaskCount(), executorContext.getMaxConcurrentTasks(),
                        jobRun.getJobRunId(), targetWorker.address());
                forwardJobToWorker(jobRun, targetWorker.address());
                return;
            }

            // 没有其他可用 Worker，将任务放回队列延迟执行
            log.warn("本地容量不足且无其他可用 Worker, 任务延迟执行: jobRunId={}, running={}/{}, delay=5s",
                    jobRun.getJobRunId(), executorContext.getRunningTaskCount(), executorContext.getMaxConcurrentTasks());
            rescheduleJobWithDelay(jobRun, 5000);
            return;
        }

        // 本地执行（SHARDING 策略在本地化架构下需要重新考虑）
        if (jobRun.getRouteStrategy() == RouteStrategy.SHARDING) {
            dispatchShardingJob(jobRun);
        } else {
            dispatchSingleJob(jobRun);
        }

        jobRun.setStatus(JobStatus.RUNNING);

        // 加入正在执行的任务索引
        runningJobIndex.computeIfAbsent(jobRun.getJobId(), k -> new HashSet<>())
                .add(jobRun.getJobRunId());
    }

    /**
     * 查找有可用容量的 Worker
     *
     * @return 有容量的 Worker 信息，如果没有返回 null
     */
    private JobSchedulerContext.WorkerCapacity findAvailableWorker() {
        List<JobSchedulerContext.WorkerCapacity> workers = schedulerContext.getAllWorkerCapacities();

        // 优先选择负载最低的 Worker
        JobSchedulerContext.WorkerCapacity bestWorker = null;
        int maxAvailableCapacity = 0;

        for (JobSchedulerContext.WorkerCapacity worker : workers) {
            if (worker.hasCapacity() && worker.availableCapacity() > maxAvailableCapacity) {
                maxAvailableCapacity = worker.availableCapacity();
                bestWorker = worker;
            }
        }

        return bestWorker;
    }

    /**
     * 转发任务到其他 Worker
     * <p>
     * 通过 Cluster Receptionist 发现目标 Worker 的 TaskReceiver，发送任务
     *
     * @param jobRun        任务信息
     * @param targetAddress 目标 Worker 地址
     */
    private void forwardJobToWorker(JobRunInfo jobRun, String targetAddress) {
        // 通过 Receptionist 查找目标 Worker 的 TaskReceiver
        // 由于跨 Worker 转发需要异步发现，这里简化处理：
        // 直接将任务放回队列，让系统自然负载均衡
        log.info("任务转发功能暂未完全实现，放回队列延迟执行: jobRunId={}", jobRun.getJobRunId());
        rescheduleJobWithDelay(jobRun, 3000);
    }

    /**
     * 将任务放回队列延迟执行
     *
     * @param jobRun  任务信息
     * @param delayMs 延迟时间（毫秒）
     */
    private void rescheduleJobWithDelay(JobRunInfo jobRun, long delayMs) {
        // 更新触发时间
        long newTriggerTime = System.currentTimeMillis() + delayMs;
        jobRun.setTriggerTime(newTriggerTime);

        // 重新加入触发队列
        triggerQueue.add(jobRun);

        // 重新调度定时器
        scheduleNextTrigger();
    }

    /**
     * 检查阻塞策略
     */
    private boolean checkBlockStrategy(JobRunInfo jobRun) {
        BlockStrategy strategy = jobRun.getBlockStrategy();
        if (strategy == null || strategy == BlockStrategy.PARALLEL) {
            return true;
        }

        Set<Long> runningJobRunIds = runningJobIndex.get(jobRun.getJobId());
        if (runningJobRunIds == null || runningJobRunIds.isEmpty()) {
            return true;
        }

        switch (strategy) {
            case DISCARD:
                log.info("阻塞策略 DISCARD: 丢弃任务 jobRunId={}, 因为 jobId={} 有 {} 个任务正在执行",
                        jobRun.getJobRunId(), jobRun.getJobId(), runningJobRunIds.size());
                return false;

            case COVER:
                log.info("阻塞策略 COVER: 取消 {} 个正在执行的任务，执行新任务 jobRunId={}",
                        runningJobRunIds.size(), jobRun.getJobRunId());
                cancelRunningJobs(jobRun.getJobId(), runningJobRunIds);
                return true;

            default:
                return true;
        }
    }

    /**
     * 取消正在执行的任务（COVER 阻塞策略）
     */
    private void cancelRunningJobs(long jobId, Set<Long> runningJobRunIds) {
        for (Long runningJobRunId : new HashSet<>(runningJobRunIds)) {
            ActorRef<ExecutorMessage> executorRef = runningExecutors.get(runningJobRunId);
            if (executorRef != null) {
                executorRef.tell(new ExecutorMessage.CancelJob(runningJobRunId, "被阻塞策略 COVER 取消"));
                log.info("发送取消任务消息: jobRunId={}, jobId={}", runningJobRunId, jobId);
            }
        }
    }

    /**
     * 分发单个任务（本地 spawn JobExecutor）
     */
    private void dispatchSingleJob(JobRunInfo jobRun) {
        log.info("本地执行任务: jobRunId={}, jobName={}",
                jobRun.getJobRunId(), jobRun.getJobName());

        // spawn 本地 JobExecutor（使用 Virtual Thread Dispatcher）
        String executorName = "executor-" + jobRun.getJobRunId() + "-" + executorIdGenerator.incrementAndGet();
        ActorRef<ExecutorMessage> executorRef = getContext().spawn(
                JobExecutor.create(executorContext, getContext().getSelf()),
                executorName,
                DispatcherSelector.fromConfig("pekko.actor.job-executor-dispatcher")
        );

        // 保存执行器引用（用于取消任务）
        runningExecutors.put(jobRun.getJobRunId(), executorRef);

        // 发送执行消息（普通任务不需要 schedulerRef，完成后通过 JobExecutor 内部机制通知）
        ExecutorMessage.ExecuteJob executeMsg = buildExecuteMessage(jobRun, 0L, 1L, null);
        executorRef.tell(executeMsg);
    }

    /**
     * 分发分片任务（Worker 自治模式）
     * <p>
     * 核心设计：
     * - Worker 根据自身能力计算 splitSize
     * - Worker 从 CRDT 获取下一个未处理范围，标记后执行
     * - 通过 CRDT 协调，避免重复处理
     * - 每个 Worker 执行完一个范围后，自动继续拆分下一个
     */
    private void dispatchShardingJob(JobRunInfo jobRun) {
        log.info("启动分片任务（自治模式）: jobRunId={}, jobName={}",
                jobRun.getJobRunId(), jobRun.getJobName());

        // 开始执行第一个分片
        executeNextSplit(jobRun);
    }

    /**
     * 执行下一个分片范围
     * <p>
     * Worker 自治拆分（非阻塞版本）：
     * 1. 计算本 Worker 的 splitSize
     * 2. 从 CRDT 获取下一个未处理的起点
     * 3. 尝试标记范围为 PROCESSING
     * 4. 如果标记成功，执行该范围
     * 5. 如果标记失败，通过 Timer 调度延迟重试（避免阻塞 Actor 线程）
     */
    private void executeNextSplit(JobRunInfo jobRun) {
        executeNextSplitWithRetry(jobRun, 0, 10);
    }

    /**
     * 带重试的分片执行（非阻塞）
     *
     * @param jobRun     任务信息
     * @param retryCount 已重试次数
     * @param backoffMs  退让时间（毫秒）
     */
    private void executeNextSplitWithRetry(JobRunInfo jobRun, int retryCount, long backoffMs) {
        long jobRunId = jobRun.getJobRunId();
        int maxRetries = 10;

        if (retryCount >= maxRetries) {
            log.warn("分片标记重试次数过多，暂停: jobRunId={}, retries={}", jobRunId, retryCount);
            return;
        }

        // 1. 计算本 Worker 的 splitSize
        long splitSize = splitCalculator.calculate();

        // 2. 从 CRDT 获取下一个未处理的起点
        long nextStart = splitLocalCache.getNextStart(jobRunId);

        // 3. 计算分片范围
        long splitStart = nextStart;
        long splitEnd = splitStart + splitSize;

        log.info("尝试执行分片: jobRunId={}, range=[{}, {}), splitSize={}, retry={}",
                jobRunId, splitStart, splitEnd, splitSize, retryCount);

        // 4. 尝试标记范围为 PROCESSING
        if (splitLocalCache.tryMarkProcessing(jobRunId, splitStart, splitEnd)) {
            // 标记成功，执行分片
            splitLocalCache.updateNextStart(jobRunId, splitEnd);
            runningShardingJobs.merge(jobRunId, 1, Integer::sum);
            doExecuteSplit(jobRun, splitStart, splitEnd);
            return;
        }

        // 标记失败，更新起点，通过 Timer 延迟重试（不阻塞 Actor 线程）
        log.info("分片范围被其他 Worker 抢占，延迟重试: jobRunId={}, range=[{}, {}), backoff={}ms",
                jobRunId, splitStart, splitEnd, backoffMs);
        splitLocalCache.updateNextStart(jobRunId, splitEnd);

        // 使用 Timer 调度延迟消息，避免 Thread.sleep 阻塞
        var self = getContext().getSelf();
        long nextBackoffMs = Math.min(backoffMs * 2, 1000);
        timer.newTimeout(timeout -> {
            self.tell(new RetrySplit(jobRunId, retryCount + 1, nextBackoffMs));
        }, backoffMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 处理分片重试消息
     * <p>
     * 由 Timer 触发，继续尝试执行分片
     */
    private Behavior<SchedulerMessage> onRetrySplit(RetrySplit msg) {
        JobRunInfo jobRun = jobRunMap.get(msg.jobRunId());
        if (jobRun == null) {
            log.warn("分片重试时任务不存在: jobRunId={}", msg.jobRunId());
            return this;
        }

        if (jobRun.getRouteStrategy() != RouteStrategy.SHARDING) {
            log.warn("分片重试时任务非分片类型: jobRunId={}", msg.jobRunId());
            return this;
        }

        executeNextSplitWithRetry(jobRun, msg.retryCount(), msg.backoffMs());
        return this;
    }

    /**
     * 执行分片任务
     */
    private void doExecuteSplit(JobRunInfo jobRun, long splitStart, long splitEnd) {
        long jobRunId = jobRun.getJobRunId();

        // 本地执行分片（使用 Virtual Thread Dispatcher）
        String executorName = "executor-" + jobRunId + "-" + splitStart + "-" + executorIdGenerator.incrementAndGet();
        ActorRef<ExecutorMessage> executorRef = getContext().spawn(
                JobExecutor.create(executorContext, getContext().getSelf()),
                executorName,
                DispatcherSelector.fromConfig("pekko.actor.job-executor-dispatcher")
        );

        // 保存执行器引用
        runningExecutors.put(jobRunId, executorRef);

        // 发送执行消息
        ExecutorMessage.ExecuteJob executeMsg = buildExecuteMessage(jobRun, splitStart, splitEnd, getContext().getSelf());
        executorRef.tell(executeMsg);

        log.info("分片执行中: jobRunId={}, range=[{}, {}), executor={}",
                jobRunId, splitStart, splitEnd, executorName);
    }

    /**
     * 构建 ExecuteJob 消息
     *
     * @param jobRun       任务信息
     * @param splitStart   分片起点（包含）
     * @param splitEnd     分片终点（不包含）
     * @param schedulerRef 调度者引用（分片任务完成后回报，普通任务可为 null）
     */
    private ExecutorMessage.ExecuteJob buildExecuteMessage(JobRunInfo jobRun, long splitStart, long splitEnd,
                                                            ActorRef<SchedulerMessage> schedulerRef) {
        return new ExecutorMessage.ExecuteJob(
                jobRun.getJobRunId(),
                jobRun.getWorkflowRunId(),
                jobRun.getJobId(),
                jobRun.getNamespaceId(),
                jobRun.getJobName(),
                jobRun.getJobType(),
                jobRun.getJobParams(),
                jobRun.getRouteStrategy(),
                jobRun.getTimeoutSeconds(),
                jobRun.getMaxRetryTimes(),
                jobRun.getRetryInterval(),
                jobRun.getRetryCount(),
                splitStart,
                splitEnd,
                schedulerRef
        );
    }

    /**
     * 设置下一个定时器
     */
    private void scheduleNextTrigger() {
        if (currentTimeout != null) {
            currentTimeout.cancel();
        }

        if (triggerQueue.isEmpty()) {
            return;
        }

        JobRunInfo next = triggerQueue.peek();
        long delay = Math.max(0, next.getTriggerTime() - System.currentTimeMillis());

        var self = getContext().getSelf();
        currentTimeout = timer.newTimeout(timeout -> {
            self.tell(new TimerFired());
        }, delay, TimeUnit.MILLISECONDS);

        log.debug("设置定时器: delay={}ms, nextJobRunId={}", delay, next.getJobRunId());
    }

    /**
     * 加载任务到内存
     * <p>
     * 线程安全：只在 Actor 消息处理线程中调用
     * <p>
     * 内存保护：当内存中任务数超过 maxPendingTasks 时拒绝加载新任务
     */
    private void loadJobRun(JobRunInfo jobRun) {
        // 内存保护：检查任务数是否超限
        if (jobRunMap.size() >= maxPendingTasks) {
            log.warn("内存中任务数已达上限 {}，拒绝加载新任务: jobRunId={}, bucketId={}",
                    maxPendingTasks, jobRun.getJobRunId(), jobRun.getBucketId());
            return;
        }

        // 检查是否属于自己的 Bucket
        if (!myBuckets.contains(jobRun.getBucketId())) {
            log.debug("任务不属于当前 Worker 的 Bucket，跳过: jobRunId={}, bucketId={}",
                    jobRun.getJobRunId(), jobRun.getBucketId());
            return;
        }

        // 检查是否已存在（重跑场景）
        JobRunInfo existing = jobRunMap.get(jobRun.getJobRunId());
        if (existing != null) {
            // 更新状态
            existing.setStatus(jobRun.getStatus());
            // 重新加入触发队列
            if (jobRun.getStatus() == JobStatus.WAITING) {
                triggerQueue.add(existing);
                log.info("任务被重跑，重新加入调度队列: jobRunId={}", jobRun.getJobRunId());
            }
            return;
        }

        jobRunMap.put(jobRun.getJobRunId(), jobRun);

        // 构建 Bucket → 任务索引
        bucketJobIndex.computeIfAbsent(jobRun.getBucketId(), k -> new HashSet<>())
                .add(jobRun.getJobRunId());

        // 构建 Workflow → 任务索引
        workflowJobIndex.computeIfAbsent(jobRun.getWorkflowId(), k -> new HashSet<>())
                .add(jobRun.getJobRunId());

        // 构建下游索引
        if (jobRun.hasDependencies()) {
            for (Long parentRunId : jobRun.getParentJobRunIds()) {
                downstreamIndex.computeIfAbsent(parentRunId, k -> new HashSet<>())
                        .add(jobRun.getJobRunId());
            }
        }

        // 添加到触发队列
        if (jobRun.getStatus() == JobStatus.WAITING) {
            triggerQueue.add(jobRun);
        }
    }

    /**
     * 处理取消工作流消息（下线信号）
     * <p>
     * 所有 Worker 收到下线信号后，各自处理自己负责的任务：
     * 1. 等待中的任务：从队列移除，标记为 CANCELLED
     * 2. 运行中的任务：发送 Kill 消息给 Executor，标记为 KILLED
     */
    private Behavior<SchedulerMessage> onCancelWorkflow(CancelWorkflow msg) {
        long workflowId = msg.workflowId();

        // 从索引快速获取该工作流的所有任务
        Set<Long> jobRunIds = workflowJobIndex.remove(workflowId);
        if (jobRunIds == null || jobRunIds.isEmpty()) {
            log.debug("本 Worker 没有该工作流的任务: workflowId={}", workflowId);
            return this;
        }

        int cancelled = 0;
        int killed = 0;

        for (Long jobRunId : jobRunIds) {
            JobRunInfo jobRun = jobRunMap.get(jobRunId);
            if (jobRun == null) {
                continue;
            }

            JobStatus status = jobRun.getStatus();

            if (status == JobStatus.WAITING) {
                // 等待中：从队列移除，更新状态
                triggerQueue.remove(jobRun);
                schedulerContext.updateJobRunStatus(jobRunId, JobStatus.CANCEL, "工作流下线");
                cleanupCompletedJob(jobRunId, jobRun);
                cancelled++;

            } else if (status == JobStatus.RUNNING) {
                // 运行中：发送取消消息
                ActorRef<ExecutorMessage> executorRef = runningExecutors.get(jobRunId);
                if (executorRef != null) {
                    executorRef.tell(new ExecutorMessage.CancelJob(jobRunId, "工作流下线"));
                }
                killed++;
            }
        }

        log.info("处理工作流下线: workflowId={}, cancelled={}, killed={}", workflowId, cancelled, killed);
        return this;
    }

    /**
     * 处理任务定义刷新
     * <p>
     * Server 修改 job_info 后广播通知，遍历 jobRunMap 刷新匹配的任务
     */
    private Behavior<SchedulerMessage> onRefreshJobInfo(SchedulerMessage.RefreshJobInfo msg) {
        long jobId = msg.jobId();
        String op = msg.op();

        log.info("收到任务定义刷新通知: jobId={}, op={}", jobId, op);

        // 遍历 jobRunMap，刷新匹配的任务
        int refreshed = 0;
        for (JobRunInfo jobRun : jobRunMap.values()) {
            if (jobRun.getJobId() == jobId) {
                // 重新从缓存获取最新的 job_info 数据
                schedulerContext.enrichJobInfo(jobRun);
                refreshed++;
            }
        }

        if (refreshed > 0) {
            log.info("刷新任务定义完成: jobId={}, refreshed={}", jobId, refreshed);
        }

        return this;
    }
}
