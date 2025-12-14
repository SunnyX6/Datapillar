package com.sunny.job.core.message;

import java.io.Serializable;

/**
 * 调度器消息协议
 * <p>
 * 定义 JobScheduler（本地 Actor）接收的所有消息类型
 * <p>
 * 去中心化设计：每个 Worker 有一个本地 JobScheduler
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public sealed interface SchedulerMessage extends Serializable {

    /**
     * 注册任务到定时器
     * <p>
     * 来源：Entity 完成任务后创建下一个 job_run，通知本地 Scheduler 注册定时事件
     *
     * @param jobRunId    任务执行实例 ID
     * @param triggerTime 计划触发时间（毫秒）
     * @param priority    优先级
     */
    record RegisterJob(
            long jobRunId,
            long triggerTime,
            int priority
    ) implements SchedulerMessage {}

    /**
     * 任务完成通知
     * <p>
     * 来源：Entity 执行完任务后通知本地 Scheduler
     * Scheduler 收到后检查下游依赖（同 Bucket 内），满足则立即分发
     *
     * @param jobRunId      任务执行实例 ID
     * @param workflowRunId 工作流执行实例 ID
     * @param status        完成状态 (SUCCESS/FAIL/TIMEOUT)
     * @param message       执行结果消息
     */
    record JobCompleted(
            long jobRunId,
            long workflowRunId,
            int status,
            String message
    ) implements SchedulerMessage {}

    /**
     * 定时器触发
     * <p>
     * 内部消息：TimerWheel 到期后触发
     * Scheduler 收到后检查依赖，满足则分发给 Entity
     */
    record TimerFired() implements SchedulerMessage {}

    /**
     * 启动扫描
     * <p>
     * Scheduler 启动时触发，认领 Bucket 并加载待执行的任务
     */
    record StartScan() implements SchedulerMessage {}

    /**
     * 工作流完成通知
     * <p>
     * 来源：Entity 检测到工作流所有任务完成后通知
     *
     * @param workflowRunId 工作流执行实例 ID
     * @param status        完成状态
     * @param message       执行结果消息
     */
    record WorkflowCompleted(
            long workflowRunId,
            int status,
            String message
    ) implements SchedulerMessage {}

    /**
     * Bucket 获得通知
     * <p>
     * 来源：BucketStateManager 检测到获得新 Bucket
     *
     * @param bucketId Bucket ID
     */
    record BucketAcquired(int bucketId) implements SchedulerMessage {}

    /**
     * Bucket 丢失通知
     * <p>
     * 来源：BucketStateManager 检测到丢失 Bucket
     *
     * @param bucketId Bucket ID
     */
    record BucketLost(int bucketId) implements SchedulerMessage {}

    /**
     * Bucket 续租
     * <p>
     * 内部消息：定时器触发续租所有持有的 Bucket
     */
    record RenewBuckets() implements SchedulerMessage {}

    /**
     * 分片范围完成报告
     * <p>
     * 来源：Worker 执行完分片范围后，更新 CRDT 状态
     * 调度者监听 CRDT 变化，汇总分片结果
     *
     * @param jobRunId      任务执行实例 ID
     * @param splitStart    分片起点
     * @param splitEnd      分片终点
     * @param status        完成状态
     * @param message       执行结果消息
     * @param workerAddress 执行该分片的 Worker 地址
     */
    record SplitCompleted(
            long jobRunId,
            long splitStart,
            long splitEnd,
            int status,
            String message,
            String workerAddress
    ) implements SchedulerMessage {}

    /**
     * 查询分片任务执行器列表
     * <p>
     * 内部消息：用于从 Receptionist 获取所有已注册的 ShardingTaskReceiver
     *
     * @param receivers 已注册的执行器列表
     */
    record ShardingReceiversUpdated(
            java.util.Set<org.apache.pekko.actor.typed.ActorRef<ExecutorMessage>> receivers
    ) implements SchedulerMessage {}

    /**
     * 批量任务加载完成
     * <p>
     * 内部消息：异步 DB 查询完成后，将结果投递回 Actor
     *
     * @param jobs     加载的任务列表
     * @param newMaxId 新的最大 ID（用于增量加载）
     * @param source   来源标识（init/bucket/incremental/rerun）
     */
    record JobsLoaded(
            java.util.List<JobRunInfo> jobs,
            long newMaxId,
            String source
    ) implements SchedulerMessage {}

    /**
     * 任务加载失败
     * <p>
     * 内部消息：异步 DB 查询失败时投递
     *
     * @param reason 失败原因
     * @param source 来源标识
     */
    record JobsLoadFailed(
            String reason,
            String source
    ) implements SchedulerMessage {}

    /**
     * 分片重试
     * <p>
     * 内部消息：分片标记失败后延迟重试
     * 使用 Timer 调度而非 Thread.sleep，避免阻塞 Actor 线程
     *
     * @param jobRunId   任务执行实例 ID
     * @param retryCount 已重试次数
     * @param backoffMs  下次退让时间（毫秒）
     */
    record RetrySplit(
            long jobRunId,
            int retryCount,
            long backoffMs
    ) implements SchedulerMessage {}
}
