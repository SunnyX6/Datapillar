package com.sunny.job.worker.pekko.actor;

import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.message.ExecutorMessage;
import com.sunny.job.core.message.ExecutorMessage.ExecuteJob;
import com.sunny.job.core.message.SchedulerMessage;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.receptionist.Receptionist;
import org.apache.pekko.actor.typed.receptionist.ServiceKey;
import org.apache.pekko.cluster.typed.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * 分片任务接收器
 * <p>
 * 每个 Worker 启动时创建，注册到 Cluster Receptionist
 * 用于接收来自其他 Worker 的分片任务
 * <p>
 * 职责：
 * - 注册自己到 Receptionist，供其他 Worker 发现
 * - 接收远程分片任务
 * - 使用 JobExecutorContext 同步执行
 * - 执行完成后发送 SplitCompleted 给调度者
 *
 * @author SunnyX6
 * @date 2025-12-14
 */
public class ShardingTaskReceiver extends AbstractBehavior<ExecutorMessage> {

    private static final Logger log = LoggerFactory.getLogger(ShardingTaskReceiver.class);

    /**
     * 服务注册 Key，用于 Receptionist 发现
     */
    public static final ServiceKey<ExecutorMessage> SERVICE_KEY =
            ServiceKey.create(ExecutorMessage.class, "sharding-task-receiver");

    private final JobExecutorContext executorContext;
    private final String selfAddress;

    public static Behavior<ExecutorMessage> create(JobExecutorContext executorContext) {
        return Behaviors.setup(ctx -> new ShardingTaskReceiver(ctx, executorContext));
    }

    private ShardingTaskReceiver(ActorContext<ExecutorMessage> context,
                                  JobExecutorContext executorContext) {
        super(context);
        this.executorContext = executorContext;
        this.selfAddress = Cluster.get(context.getSystem()).selfMember().address().toString();

        // 注册到 Receptionist
        context.getSystem().receptionist().tell(
                Receptionist.register(SERVICE_KEY, context.getSelf())
        );

        log.info("ShardingTaskReceiver 启动，已注册到 Receptionist: {}", selfAddress);
    }

    @Override
    public Receive<ExecutorMessage> createReceive() {
        return newReceiveBuilder()
                .onMessage(ExecuteJob.class, this::onExecuteJob)
                .build();
    }

    /**
     * 处理分片任务执行请求
     */
    private Behavior<ExecutorMessage> onExecuteJob(ExecuteJob msg) {
        log.info("收到分片任务: jobRunId={}, splitRange=[{}, {}), from={}",
                msg.jobRunId(), msg.splitStart(), msg.splitEnd(),
                msg.schedulerRef() != null ? msg.schedulerRef().path() : "local");

        ActorRef<SchedulerMessage> schedulerRef = msg.schedulerRef();

        // 异步执行任务（使用虚拟线程）
        CompletableFuture.runAsync(() -> {
            JobExecutorContext.ExecutionResult result;
            try {
                // 同步执行任务
                result = executorContext.execute(msg);
            } catch (Exception e) {
                log.error("分片任务执行异常: jobRunId={}", msg.jobRunId(), e);
                result = JobExecutorContext.ExecutionResult.failure(e.getMessage());
            }

            log.info("分片执行完成: jobRunId={}, splitRange=[{}, {}), status={}",
                    msg.jobRunId(), msg.splitStart(), msg.splitEnd(), result.status());

            // 发送 SplitCompleted 给调度者
            if (schedulerRef != null) {
                schedulerRef.tell(new SchedulerMessage.SplitCompleted(
                        msg.jobRunId(),
                        msg.splitStart(),
                        msg.splitEnd(),
                        result.status().getCode(),
                        result.message(),
                        selfAddress
                ));
            }

            // 更新 DB 状态
            executorContext.updateJobRunStatus(msg.jobRunId(), result.status(), msg.splitStart());
        });

        return this;
    }
}
