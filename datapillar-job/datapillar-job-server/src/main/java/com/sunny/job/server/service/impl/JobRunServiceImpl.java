package com.sunny.job.server.service.impl;

import com.sunny.job.core.message.JobRunBroadcast;
import com.sunny.job.core.message.JobRunBroadcast.*;
import com.sunny.job.server.broadcast.JobRunBroadcaster;
import com.sunny.job.server.service.JobRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 任务运行实例 Service 实现
 * <p>
 * Server 核心职责：广播任务操作事件（由 Worker 处理）
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
@Service
public class JobRunServiceImpl implements JobRunService {

    private static final Logger log = LoggerFactory.getLogger(JobRunServiceImpl.class);

    private final JobRunBroadcaster jobRunBroadcaster;

    public JobRunServiceImpl(JobRunBroadcaster jobRunBroadcaster) {
        this.jobRunBroadcaster = jobRunBroadcaster;
    }

    @Override
    public void kill(Long jobRunId) {
        log.info("终止任务: jobRunId={}", jobRunId);

        KillPayload payload = new KillPayload(jobRunId);
        JobRunBroadcast event = JobRunBroadcast.kill(payload);

        jobRunBroadcaster.broadcast(event);

        log.info("任务终止请求已提交: jobRunId={}, eventId={}", jobRunId, event.getEventId());
    }

    @Override
    public void pass(Long jobRunId) {
        log.info("跳过任务: jobRunId={}", jobRunId);

        PassPayload payload = new PassPayload(jobRunId);
        JobRunBroadcast event = JobRunBroadcast.pass(payload);

        jobRunBroadcaster.broadcast(event);

        log.info("任务跳过请求已提交: jobRunId={}, eventId={}", jobRunId, event.getEventId());
    }

    @Override
    public void markFailed(Long jobRunId) {
        log.info("标记任务失败: jobRunId={}", jobRunId);

        MarkFailedPayload payload = new MarkFailedPayload(jobRunId);
        JobRunBroadcast event = JobRunBroadcast.markFailed(payload);

        jobRunBroadcaster.broadcast(event);

        log.info("任务标记失败请求已提交: jobRunId={}, eventId={}", jobRunId, event.getEventId());
    }

    @Override
    public void retry(Long jobRunId, Long jobId, Long workflowRunId, Long namespaceId, Integer bucketId) {
        log.info("重试任务: jobRunId={}, jobId={}, workflowRunId={}, bucketId={}",
                jobRunId, jobId, workflowRunId, bucketId);

        RetryPayload payload = new RetryPayload(jobRunId, jobId, workflowRunId, namespaceId, bucketId);
        JobRunBroadcast event = JobRunBroadcast.retry(payload);

        jobRunBroadcaster.broadcast(event);

        log.info("任务重试请求已提交: jobRunId={}, eventId={}", jobRunId, event.getEventId());
    }

    @Override
    public void trigger(Long jobRunId, Long jobId, Long workflowRunId, Long namespaceId, Integer bucketId) {
        log.info("手动触发任务: jobRunId={}, jobId={}, workflowRunId={}, bucketId={}",
                jobRunId, jobId, workflowRunId, bucketId);

        TriggerPayload payload = new TriggerPayload(jobRunId, jobId, workflowRunId, namespaceId, bucketId);
        JobRunBroadcast event = JobRunBroadcast.trigger(payload);

        jobRunBroadcaster.broadcast(event);

        log.info("任务触发请求已提交: jobRunId={}, eventId={}", jobRunId, event.getEventId());
    }
}
