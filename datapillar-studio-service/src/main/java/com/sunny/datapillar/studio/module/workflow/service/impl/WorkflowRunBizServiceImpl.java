package com.sunny.datapillar.studio.module.workflow.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.studio.module.workflow.dto.WorkflowDto;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowRunBizService;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 工作流Run业务服务实现
 * 实现工作流Run业务业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class WorkflowRunBizServiceImpl implements WorkflowRunBizService {

    private final WorkflowService workflowService;

    @Override
    public JsonNode triggerWorkflow(Long id, WorkflowDto.TriggerRequest request) {
        return workflowService.triggerWorkflow(id, request);
    }

    @Override
    public JsonNode getWorkflowRuns(Long id, int limit, int offset, String state) {
        return workflowService.getWorkflowRuns(id, limit, offset, state);
    }

    @Override
    public JsonNode getWorkflowRun(Long id, String runId) {
        return workflowService.getWorkflowRun(id, runId);
    }

    @Override
    public JsonNode getRunJobs(Long id, String runId) {
        return workflowService.getRunJobs(id, runId);
    }

    @Override
    public JsonNode getRunJob(Long id, String runId, String jobId) {
        return workflowService.getRunJob(id, runId, jobId);
    }

    @Override
    public JsonNode getJobLogs(Long id, String runId, String jobId, int tryNumber) {
        return workflowService.getJobLogs(id, runId, jobId, tryNumber);
    }

    @Override
    public JsonNode rerunJob(Long id, String runId, String jobId, WorkflowDto.RerunJobRequest request) {
        return workflowService.rerunJob(id, runId, jobId, request);
    }

    @Override
    public JsonNode setJobState(Long id, String runId, String jobId, WorkflowDto.SetJobStateRequest request) {
        return workflowService.setJobState(id, runId, jobId, request);
    }

    @Override
    public JsonNode clearJobs(Long id, String runId, WorkflowDto.ClearJobsRequest request) {
        return workflowService.clearJobs(id, runId, request);
    }
}
