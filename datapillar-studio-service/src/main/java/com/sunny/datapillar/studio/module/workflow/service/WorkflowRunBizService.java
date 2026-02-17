package com.sunny.datapillar.studio.module.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.studio.module.workflow.dto.WorkflowDto;

/**
 * 工作流Run业务服务
 * 提供工作流Run业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface WorkflowRunBizService {

    JsonNode triggerWorkflow(Long id, WorkflowDto.TriggerRequest request);

    JsonNode getWorkflowRuns(Long id, int limit, int offset, String state);

    JsonNode getWorkflowRun(Long id, String runId);

    JsonNode getRunJobs(Long id, String runId);

    JsonNode getRunJob(Long id, String runId, String jobId);

    JsonNode getJobLogs(Long id, String runId, String jobId, int tryNumber);

    JsonNode rerunJob(Long id, String runId, String jobId, WorkflowDto.RerunJobRequest request);

    JsonNode setJobState(Long id, String runId, String jobId, WorkflowDto.SetJobStateRequest request);

    JsonNode clearJobs(Long id, String runId, WorkflowDto.ClearJobsRequest request);
}
