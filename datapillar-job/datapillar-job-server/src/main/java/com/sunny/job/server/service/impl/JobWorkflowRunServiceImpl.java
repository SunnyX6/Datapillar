package com.sunny.job.server.service.impl;

import com.sunny.job.core.message.WorkflowBroadcast;
import com.sunny.job.core.message.WorkflowBroadcast.KillPayload;
import com.sunny.job.core.message.WorkflowBroadcast.RerunPayload;
import com.sunny.job.server.broadcast.WorkflowBroadcaster;
import com.sunny.job.server.service.JobWorkflowRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 工作流运行实例 Service 实现
 * <p>
 * Server 核心职责：广播 kill/rerun 事件（由 Worker 处理）
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
@Service
public class JobWorkflowRunServiceImpl implements JobWorkflowRunService {

    private static final Logger log = LoggerFactory.getLogger(JobWorkflowRunServiceImpl.class);

    private final WorkflowBroadcaster workflowBroadcaster;

    public JobWorkflowRunServiceImpl(WorkflowBroadcaster workflowBroadcaster) {
        this.workflowBroadcaster = workflowBroadcaster;
    }

    @Override
    public void kill(Long workflowRunId) {
        log.info("终止工作流运行实例: workflowRunId={}", workflowRunId);

        KillPayload payload = new KillPayload(workflowRunId);
        WorkflowBroadcast event = WorkflowBroadcast.kill(payload);

        workflowBroadcaster.broadcast(event);

        log.info("工作流终止请求已提交: workflowRunId={}, eventId={}", workflowRunId, event.getEventId());
    }

    @Override
    public void rerun(Long workflowId, Long workflowRunId, Map<Long, Long> jobRunIdToJobIdMap) {
        log.info("重跑工作流运行实例: workflowId={}, workflowRunId={}, jobRunCount={}",
                workflowId, workflowRunId, jobRunIdToJobIdMap != null ? jobRunIdToJobIdMap.size() : 0);

        RerunPayload payload = new RerunPayload(workflowId, workflowRunId, jobRunIdToJobIdMap);
        WorkflowBroadcast event = WorkflowBroadcast.rerun(payload);

        workflowBroadcaster.broadcast(event);

        log.info("工作流重跑请求已提交: workflowId={}, workflowRunId={}, eventId={}",
                workflowId, workflowRunId, event.getEventId());
    }
}
