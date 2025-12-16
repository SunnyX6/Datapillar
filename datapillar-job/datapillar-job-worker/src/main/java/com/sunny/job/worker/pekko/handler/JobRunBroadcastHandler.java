package com.sunny.job.worker.pekko.handler;

import com.sunny.job.core.enums.JobRunOp;
import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.message.JobRunBroadcast;
import com.sunny.job.core.message.JobRunBroadcast.*;
import com.sunny.job.core.message.SchedulerMessage;
import com.sunny.job.worker.domain.mapper.JobRunMapper;
import com.sunny.job.worker.pekko.actor.JobSchedulerManager;
import com.sunny.job.worker.pekko.ddata.BucketManager;
import com.sunny.job.worker.pekko.ddata.JobRunBroadcastState;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 任务级广播处理器
 * <p>
 * 监听 Server 广播的任务级事件，根据 Bucket 归属处理
 * <p>
 * 支持的 Op 类型：
 * - TRIGGER: 手动执行
 * - RETRY: 重试
 * - KILL: 终止
 * - PASS: 跳过
 * - MARK_FAILED: 标记失败
 * <p>
 * Bucket 归属规则：
 * - 根据 bucketId 字段判断是否由本 Worker 处理
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
@Component
public class JobRunBroadcastHandler {

    private static final Logger log = LoggerFactory.getLogger(JobRunBroadcastHandler.class);

    private final JobRunBroadcastState broadcastState;
    private final BucketManager bucketManager;
    private final JobRunMapper jobRunMapper;
    private final JobSchedulerManager schedulerManager;

    public JobRunBroadcastHandler(JobRunBroadcastState broadcastState,
                                   BucketManager bucketManager,
                                   JobRunMapper jobRunMapper,
                                   JobSchedulerManager schedulerManager) {
        this.broadcastState = broadcastState;
        this.bucketManager = bucketManager;
        this.jobRunMapper = jobRunMapper;
        this.schedulerManager = schedulerManager;
    }

    @PostConstruct
    public void init() {
        broadcastState.subscribe(this::handleBroadcast);
        log.info("JobRunBroadcastHandler 初始化完成，已订阅广播事件");
    }

    /**
     * 处理广播事件
     */
    private void handleBroadcast(JobRunBroadcast event) {
        JobRunOp op = event.getOp();
        if (op == null) {
            return;
        }

        log.info("处理广播事件: eventId={}, op={}", event.getEventId(), op);

        switch (op) {
            case TRIGGER -> handleTrigger(event.getPayloadAs(TriggerPayload.class));
            case RETRY -> handleRetry(event.getPayloadAs(RetryPayload.class));
            case KILL -> handleKill(event.getPayloadAs(KillPayload.class));
            case PASS -> handlePass(event.getPayloadAs(PassPayload.class));
            case MARK_FAILED -> handleMarkFailed(event.getPayloadAs(MarkFailedPayload.class));
            default -> log.warn("未知的 Op 类型: {}", op);
        }
    }

    /**
     * 处理手动执行事件
     */
    private void handleTrigger(TriggerPayload payload) {
        Integer bucketId = payload.bucketId();
        if (!bucketManager.hasBucket(bucketId)) {
            log.debug("本 Worker 不负责此 Bucket，跳过: bucketId={}", bucketId);
            return;
        }

        Long jobRunId = payload.jobRunId();
        Long jobId = payload.jobId();
        log.info("处理手动执行事件: jobRunId={}, jobId={}, bucketId={}",
                jobRunId, jobId, bucketId);

        // 通知 Scheduler 立即执行
        SchedulerMessage.RegisterJob registerMsg = new SchedulerMessage.RegisterJob(
                jobRunId, jobId, System.currentTimeMillis(), 0
        );
        schedulerManager.getSchedulerForBucket(bucketId).tell(registerMsg);
    }

    /**
     * 处理重试事件
     */
    private void handleRetry(RetryPayload payload) {
        Integer bucketId = payload.bucketId();
        if (!bucketManager.hasBucket(bucketId)) {
            log.debug("本 Worker 不负责此 Bucket，跳过: bucketId={}", bucketId);
            return;
        }

        Long jobRunId = payload.jobRunId();
        Long jobId = payload.jobId();
        log.info("处理重试事件: jobRunId={}, jobId={}, bucketId={}",
                jobRunId, jobId, bucketId);

        // 重置状态为 WAITING
        jobRunMapper.updateStatus(jobRunId, JobStatus.WAITING.getCode(), JobRunOp.RETRY.name(), null, null, null, null);

        // 通知 Scheduler 重新注册
        SchedulerMessage.RegisterJob registerMsg = new SchedulerMessage.RegisterJob(
                jobRunId, jobId, System.currentTimeMillis(), 0
        );
        schedulerManager.getSchedulerForBucket(bucketId).tell(registerMsg);
    }

    /**
     * 处理终止事件
     */
    private void handleKill(KillPayload payload) {
        Long jobRunId = payload.jobRunId();
        log.info("处理终止事件: jobRunId={}", jobRunId);

        // 查询 jobRun 的 bucketId
        Integer bucketId = jobRunMapper.selectBucketIdById(jobRunId);
        if (bucketId == null || !bucketManager.hasBucket(bucketId)) {
            log.debug("本 Worker 不负责此任务，跳过: jobRunId={}", jobRunId);
            return;
        }

        // 更新状态为 CANCEL
        jobRunMapper.updateStatus(jobRunId, JobStatus.CANCEL.getCode(), JobRunOp.KILL.name(), null, null, System.currentTimeMillis(), "用户终止");

        // 通知 Scheduler 取消任务
        SchedulerMessage.CancelJob cancelMsg = new SchedulerMessage.CancelJob(jobRunId);
        schedulerManager.getSchedulerForBucket(bucketId).tell(cancelMsg);

        log.info("终止任务完成: jobRunId={}, bucketId={}", jobRunId, bucketId);
    }

    /**
     * 处理跳过事件
     */
    private void handlePass(PassPayload payload) {
        Long jobRunId = payload.jobRunId();
        log.info("处理跳过事件: jobRunId={}", jobRunId);

        // 查询 jobRun 的 bucketId
        Integer bucketId = jobRunMapper.selectBucketIdById(jobRunId);
        if (bucketId == null || !bucketManager.hasBucket(bucketId)) {
            log.debug("本 Worker 不负责此任务，跳过: jobRunId={}", jobRunId);
            return;
        }

        // 更新状态为 SKIPPED
        jobRunMapper.updateStatus(jobRunId, JobStatus.SKIPPED.getCode(), JobRunOp.PASS.name(), null, null, System.currentTimeMillis(), "用户跳过");

        // 通知 Scheduler 取消任务（如果在队列中）
        SchedulerMessage.CancelJob cancelMsg = new SchedulerMessage.CancelJob(jobRunId);
        schedulerManager.getSchedulerForBucket(bucketId).tell(cancelMsg);

        log.info("跳过任务完成: jobRunId={}, bucketId={}", jobRunId, bucketId);
    }

    /**
     * 处理标记失败事件
     */
    private void handleMarkFailed(MarkFailedPayload payload) {
        Long jobRunId = payload.jobRunId();
        log.info("处理标记失败事件: jobRunId={}", jobRunId);

        // 查询 jobRun 的 bucketId
        Integer bucketId = jobRunMapper.selectBucketIdById(jobRunId);
        if (bucketId == null || !bucketManager.hasBucket(bucketId)) {
            log.debug("本 Worker 不负责此任务，跳过: jobRunId={}", jobRunId);
            return;
        }

        // 更新状态为 FAIL
        jobRunMapper.updateStatus(jobRunId, JobStatus.FAIL.getCode(), JobRunOp.MARK_FAILED.name(), null, null, System.currentTimeMillis(), "用户标记失败");

        // 通知 Scheduler 取消任务（如果在队列中）
        SchedulerMessage.CancelJob cancelMsg = new SchedulerMessage.CancelJob(jobRunId);
        schedulerManager.getSchedulerForBucket(bucketId).tell(cancelMsg);

        log.info("标记失败完成: jobRunId={}, bucketId={}", jobRunId, bucketId);
    }
}
