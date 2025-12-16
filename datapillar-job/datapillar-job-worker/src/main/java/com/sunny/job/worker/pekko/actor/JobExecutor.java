package com.sunny.job.worker.pekko.actor;

import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.enums.RouteStrategy;
import com.sunny.job.core.message.JobRunInfo;
import com.sunny.job.core.message.SchedulerMessage;
import com.sunny.job.core.message.ExecutorMessage;
import com.sunny.job.core.message.ExecutorMessage.*;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * 任务执行器（本地 Actor）
 * <p>
 * 本地化设计：
 * - 由 JobScheduler 直接 spawn 创建
 * - 执行完成后通知 JobScheduler
 * - 执行完成后自动终止
 * <p>
 * 职责：
 * - 接收 ExecuteJob 消息，执行任务
 * - 超时控制（Actor Timer）
 * - 更新 DB 状态
 * - 发送 JobCompleted 给 JobScheduler
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public class JobExecutor extends AbstractBehavior<ExecutorMessage> {

    private static final Logger log = LoggerFactory.getLogger(JobExecutor.class);

    private final TimerScheduler<ExecutorMessage> timers;
    private final JobExecutorContext executorContext;
    private final ActorRef<SchedulerMessage> scheduler;

    /**
     * 当前执行的任务信息
     */
    private ExecuteJob currentJob;

    /**
     * 是否已取消
     */
    private volatile boolean cancelled = false;

    public static Behavior<ExecutorMessage> create(JobExecutorContext executorContext,
                                                    ActorRef<SchedulerMessage> scheduler) {
        return Behaviors.setup(ctx ->
                Behaviors.withTimers(timers ->
                        new JobExecutor(ctx, timers, executorContext, scheduler)));
    }

    private JobExecutor(ActorContext<ExecutorMessage> context,
                        TimerScheduler<ExecutorMessage> timers,
                        JobExecutorContext executorContext,
                        ActorRef<SchedulerMessage> scheduler) {
        super(context);
        this.timers = timers;
        this.executorContext = executorContext;
        this.scheduler = scheduler;

        log.debug("JobExecutor 创建");
    }

    @Override
    public Receive<ExecutorMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(ExecuteJob.class, this::onExecuteJob)
                .onMessage(CancelJob.class, this::onCancelJob)
                .onMessage(ExecutionTimeout.class, this::onExecutionTimeout)
                .onMessage(QueryStatus.class, this::onQueryStatus)
                .build();
    }

    /**
     * 处理执行任务
     * <p>
     * 同步执行（在 Virtual Thread Dispatcher 上，不会阻塞其他 Actor）
     */
    private Behavior<ExecutorMessage> onExecuteJob(ExecuteJob msg) {
        log.info("开始执行任务: jobRunId={}, jobName={}, splitRange=[{}, {})",
                msg.jobRunId(), msg.jobName(), msg.splitStart(), msg.splitEnd());

        this.currentJob = msg;
        this.cancelled = false;

        // 更新 workflow_run 的 nextTriggerTime（首个任务开始执行时）
        executorContext.updateNextTriggerTimeIfNeeded(msg.workflowRunId());

        // 更新 DB 状态为 RUNNING
        executorContext.updateJobRunStatus(msg.jobRunId(), JobStatus.RUNNING, msg.splitStart());

        // 设置超时定时器
        if (msg.timeoutSeconds() > 0) {
            timers.startSingleTimer(
                    "timeout-" + msg.jobRunId(),
                    new ExecutionTimeout(msg.jobRunId()),
                    Duration.ofSeconds(msg.timeoutSeconds())
            );
        }

        // 同步执行任务（在 Pekko Virtual Thread Dispatcher 上）
        JobExecutorContext.ExecutionResult result = executorContext.execute(msg);

        // 处理执行结果
        handleExecutionResult(msg, result.status(), result.message());

        return Behaviors.stopped();
    }

    /**
     * 处理任务执行结果
     */
    private void handleExecutionResult(ExecuteJob job, JobStatus status, String message) {
        if (cancelled) {
            log.info("任务已取消，忽略执行结果: jobRunId={}", job.jobRunId());
            return;
        }

        // 取消超时定时器
        timers.cancel("timeout-" + job.jobRunId());

        log.info("任务执行完成: jobRunId={}, status={}, splitRange=[{}, {})",
                job.jobRunId(), status, job.splitStart(), job.splitEnd());

        // 处理重试逻辑
        if (status == JobStatus.FAIL && canRetry(job)) {
            handleRetry(job);
            return;
        }

        // 更新 DB 状态
        executorContext.updateJobRunStatus(job.jobRunId(), status, job.splitStart());

        // 处理分片任务的完成逻辑
        if (job.routeStrategy() == RouteStrategy.SHARDING) {
            handleShardingComplete(job, status, message);
        } else {
            // 非分片任务，直接通知 Scheduler
            notifyScheduler(job, status, message);
        }

        this.currentJob = null;
    }

    /**
     * 处理分片任务完成
     * <p>
     * Worker 自治模式：执行完分片后，发送 SplitCompleted 消息
     * Scheduler 收到后更新 CRDT 状态，并继续拆分下一个范围
     */
    private void handleShardingComplete(ExecuteJob job, JobStatus status, String message) {
        log.info("分片执行完成: jobRunId={}, splitRange=[{}, {}), status={}",
                job.jobRunId(), job.splitStart(), job.splitEnd(), status);

        // 发送 SplitCompleted 消息给 Scheduler
        // schedulerRef 是 Scheduler 在 dispatchShardingJob 时传入的引用
        ActorRef<SchedulerMessage> schedulerRef = job.schedulerRef();
        if (schedulerRef != null) {
            // 获取当前 Worker 地址
            String workerAddress = getContext().getSelf().path().address().toString();

            schedulerRef.tell(new SchedulerMessage.SplitCompleted(
                    job.jobRunId(),
                    job.splitStart(),
                    job.splitEnd(),
                    status.getCode(),
                    message,
                    workerAddress
            ));
        } else {
            // schedulerRef 为 null，说明是本地调度，直接通知本地 scheduler
            scheduler.tell(new SchedulerMessage.SplitCompleted(
                    job.jobRunId(),
                    job.splitStart(),
                    job.splitEnd(),
                    status.getCode(),
                    message,
                    getContext().getSelf().path().address().toString()
            ));
        }
    }

    /**
     * 通知 Scheduler 任务完成
     */
    private void notifyScheduler(ExecuteJob job, JobStatus status, String message) {
        scheduler.tell(new SchedulerMessage.JobCompleted(
                job.jobRunId(),
                job.workflowRunId(),
                status.getCode(),
                message
        ));

        // 触发告警（失败或超时时）
        if (status == JobStatus.FAIL || status == JobStatus.TIMEOUT) {
            executorContext.triggerAlert(
                    job.jobId(),
                    job.jobRunId(),
                    job.workflowRunId(),
                    job.namespaceId(),
                    job.jobName(),
                    status,
                    message
            );
        }

        // 检查 workflow_run 是否完成，如果完成则生成下一个
        checkAndGenerateNextWorkflowRun(job.workflowRunId());
    }

    /**
     * 检查 workflow_run 是否完成，如果完成则更新状态并生成下一个
     * <p>
     * 核心逻辑（DDL 设计）：
     * 1. 检查 workflow_run 下所有 job_run 是否都已完成
     * 2. 计算 workflow_run 最终状态
     * 3. 更新 workflow_run 状态
     * 4. 若成功且 workflow 仍上线，生成下一个 workflow_run + job_run
     * 5. 通知 Scheduler 注册新任务
     */
    private void checkAndGenerateNextWorkflowRun(long workflowRunId) {
        // 1. 检查是否所有 job_run 都已完成
        if (!executorContext.checkWorkflowRunCompleted(workflowRunId)) {
            log.debug("工作流尚未完成，等待其他任务: workflowRunId={}", workflowRunId);
            return;
        }

        log.info("工作流所有任务完成，开始处理: workflowRunId={}", workflowRunId);

        // 2. 计算最终状态
        JobStatus finalStatus = executorContext.calculateWorkflowRunFinalStatus(workflowRunId);

        // 3. 更新 workflow_run 状态
        executorContext.updateWorkflowRunStatus(workflowRunId, finalStatus,
                finalStatus.isSuccess() ? "所有任务执行成功" : "存在失败任务");

        // 4. 通知 Scheduler 工作流完成
        scheduler.tell(new SchedulerMessage.WorkflowCompleted(
                workflowRunId,
                finalStatus.getCode(),
                finalStatus.isSuccess() ? "成功" : "失败"
        ));

        // 5. 若成功，生成下一个 workflow_run
        if (finalStatus.isSuccess()) {
            JobExecutorContext.GenerateNextResult result = executorContext.generateNextWorkflowRun(workflowRunId);

            if (result.generated()) {
                log.info("成功生成下一个工作流执行实例: workflowRunId={}, nextTriggerTime={}",
                        result.nextWorkflowRunId(), result.nextTriggerTime());

                // 6. 通知 Scheduler 注册新任务（携带完整的 JobRunInfo）
                for (JobRunInfo jobRunInfo : result.jobRunInfoList()) {
                    scheduler.tell(new SchedulerMessage.RegisterJob(
                            jobRunInfo.getJobRunId(),
                            jobRunInfo.getJobId(),
                            jobRunInfo.getTriggerTime(),
                            jobRunInfo.getPriority(),
                            jobRunInfo
                    ));
                }
            }
        } else {
            log.info("工作流执行失败，不生成下一次: workflowRunId={}", workflowRunId);
        }
    }

    /**
     * 判断是否可以重试
     */
    private boolean canRetry(ExecuteJob job) {
        return job.retryCount() < job.maxRetryTimes();
    }

    /**
     * 处理重试
     */
    private void handleRetry(ExecuteJob job) {
        int newRetryCount = job.retryCount() + 1;
        log.info("任务失败，准备重试: jobRunId={}, retryCount={}/{}",
                job.jobRunId(), newRetryCount, job.maxRetryTimes());

        // 更新 DB：retry_count + 1, status = WAITING
        executorContext.updateJobRunForRetry(job.jobRunId(), newRetryCount);

        // 计算下次触发时间
        long nextTriggerTime = System.currentTimeMillis() + job.retryInterval() * 1000L;

        // 通知 Scheduler 重新注册
        scheduler.tell(new SchedulerMessage.RegisterJob(
                job.jobRunId(),
                job.jobId(),
                nextTriggerTime,
                0
        ));

        // 停止自己
        getContext().stop(getContext().getSelf());
    }

    /**
     * 处理取消任务
     */
    private Behavior<ExecutorMessage> onCancelJob(CancelJob msg) {
        log.info("取消任务: jobRunId={}, reason={}", msg.jobRunId(), msg.reason());

        this.cancelled = true;
        timers.cancel("timeout-" + msg.jobRunId());

        // 中断执行
        executorContext.cancelExecution(msg.jobRunId());

        // 更新 DB 状态
        executorContext.updateJobRunStatus(msg.jobRunId(), JobStatus.CANCEL, -1L);

        // 通知 Scheduler
        if (currentJob != null) {
            notifyScheduler(currentJob, JobStatus.CANCEL, msg.reason());
        }

        this.currentJob = null;

        // 停止自己
        return Behaviors.stopped();
    }

    /**
     * 处理执行超时
     */
    private Behavior<ExecutorMessage> onExecutionTimeout(ExecutionTimeout msg) {
        log.warn("任务执行超时: jobRunId={}", msg.jobRunId());

        this.cancelled = true;

        // 中断执行
        executorContext.cancelExecution(msg.jobRunId());

        // 更新 DB 状态（超时不重试）
        executorContext.updateJobRunStatus(msg.jobRunId(), JobStatus.TIMEOUT, -1L);

        // 通知 Scheduler
        if (currentJob != null) {
            notifyScheduler(currentJob, JobStatus.TIMEOUT, "执行超时");
        }

        this.currentJob = null;

        // 停止自己
        return Behaviors.stopped();
    }

    /**
     * 处理状态查询
     */
    private Behavior<ExecutorMessage> onQueryStatus(QueryStatus msg) {
        log.debug("查询任务状态: jobRunId={}", msg.jobRunId());
        // 可以返回当前状态，用于健康检查
        return this;
    }
}
