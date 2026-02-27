package com.sunny.datapillar.studio.module.workflow.service;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工作流Run业务服务
 * 提供工作流Run业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface WorkflowRunBizService {

    JsonNode triggerWorkflow(Long id, WorkflowTriggerRequest request);

    JsonNode getWorkflowRuns(Long id, int limit, int offset, String state);

    JsonNode getWorkflowRun(Long id, String runId);

    JsonNode getRunJobs(Long id, String runId);

    JsonNode getRunJob(Long id, String runId, String jobId);

    JsonNode getJobLogs(Long id, String runId, String jobId, int tryNumber);

    JsonNode rerunJob(Long id, String runId, String jobId, WorkflowRerunJobRequest request);

    JsonNode setJobState(Long id, String runId, String jobId, WorkflowSetJobStatusRequest request);

    JsonNode clearJobs(Long id, String runId, WorkflowClearJobsRequest request);
}
