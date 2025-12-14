package com.sunny.job.core.message;

import com.sunny.job.core.enums.RouteStrategy;

import java.io.Serializable;

/**
 * Executor Entity 消息协议
 * <p>
 * 定义 TaskExecutorEntity (Sharding) 接收的所有消息类型
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public sealed interface ExecutorMessage extends Serializable {

    /**
     * 执行任务命令
     * <p>
     * 来源：Dispatcher 分发任务时发送
     *
     * @param jobRunId        任务执行实例 ID
     * @param workflowRunId   工作流执行实例 ID
     * @param jobId           任务定义 ID
     * @param namespaceId     命名空间 ID
     * @param jobName         任务名称
     * @param jobType         任务类型 (String，支持用户扩展)
     * @param jobParams       任务参数 (JSON)
     * @param routeStrategy   路由策略
     * @param timeoutSeconds  超时时间（秒）
     * @param maxRetryTimes   最大重试次数
     * @param retryInterval   重试间隔（秒）
     * @param retryCount      当前重试次数
     * @param splitStart      分片起点（SHARDING 策略时使用，包含）
     * @param splitEnd        分片终点（SHARDING 策略时使用，不包含）
     * @param schedulerRef    调度者引用（分片任务完成后回报，本地任务可为 null）
     */
    record ExecuteJob(
            long jobRunId,
            long workflowRunId,
            long jobId,
            long namespaceId,
            String jobName,
            String jobType,
            String jobParams,
            RouteStrategy routeStrategy,
            int timeoutSeconds,
            int maxRetryTimes,
            int retryInterval,
            int retryCount,
            long splitStart,
            long splitEnd,
            org.apache.pekko.actor.typed.ActorRef<SchedulerMessage> schedulerRef
    ) implements ExecutorMessage {}

    /**
     * 取消任务命令
     * <p>
     * 来源：Dispatcher 根据阻塞策略（COVER）取消正在执行的任务
     *
     * @param jobRunId  任务执行实例 ID
     * @param reason    取消原因
     */
    record CancelJob(
            long jobRunId,
            String reason
    ) implements ExecutorMessage {}

    /**
     * 查询任务状态
     * <p>
     * 用于健康检查或状态同步
     *
     * @param jobRunId  任务执行实例 ID
     */
    record QueryStatus(
            long jobRunId
    ) implements ExecutorMessage {}

    /**
     * 执行超时信号
     * <p>
     * 内部消息：Entity 内部 Timer 到期后触发
     *
     * @param jobRunId  任务执行实例 ID
     */
    record ExecutionTimeout(
            long jobRunId
    ) implements ExecutorMessage {}
}
