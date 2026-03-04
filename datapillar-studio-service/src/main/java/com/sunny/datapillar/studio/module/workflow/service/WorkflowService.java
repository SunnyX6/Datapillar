package com.sunny.datapillar.studio.module.workflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * Workflow services Provide workflow business capabilities and domain services
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface WorkflowService {

  // ==================== Workflow CRUD ====================

  /** Paginated query workflow list */
  IPage<WorkflowListItemResponse> getWorkflowPage(
      Page<WorkflowListItemResponse> page, Long projectId, String workflowName, Integer status);

  /** Get workflow details（Contains tasks and dependencies） */
  WorkflowResponse getWorkflowDetail(Long id);

  /** Create workflow */
  Long createWorkflow(WorkflowCreateRequest dto);

  /** Update workflow */
  void updateWorkflow(Long id, WorkflowUpdateRequest dto);

  /** Delete workflow（Delete simultaneouslyAirflow DAG） */
  void deleteWorkflow(Long id);

  // ==================== DAG management ====================

  /** Publish workflow to Airflow */
  void publishWorkflow(Long id);

  /** Pause workflow */
  void pauseWorkflow(Long id);

  /** Recovery workflow */
  void resumeWorkflow(Long id);

  /** GetDAGDetails（fromAirflow） */
  JsonNode getDagDetail(Long id);

  /** GetDAGVersion list */
  JsonNode getDagVersions(Long id, int limit, int offset);

  /** GetDAGSpecific version details */
  JsonNode getDagVersion(Long id, int versionNumber);

  // ==================== DAG Run management ====================

  /** Trigger workflow to run */
  JsonNode triggerWorkflow(Long id, WorkflowTriggerRequest request);

  /** Get run list */
  JsonNode getWorkflowRuns(Long id, int limit, int offset, String state);

  /** Get run details */
  JsonNode getWorkflowRun(Long id, String runId);

  // ==================== Job management ====================

  /** Get task list */
  JsonNode getRunJobs(Long id, String runId);

  /** Get task details */
  JsonNode getRunJob(Long id, String runId, String jobId);

  /** Get task log */
  JsonNode getJobLogs(Long id, String runId, String jobId, int tryNumber);

  /** rerun mission */
  JsonNode rerunJob(Long id, String runId, String jobId, WorkflowRerunJobRequest request);

  /** Set task status */
  JsonNode setJobState(Long id, String runId, String jobId, WorkflowSetJobStatusRequest request);

  /** Batch cleaning tasks */
  JsonNode clearJobs(Long id, String runId, WorkflowClearJobsRequest request);
}
