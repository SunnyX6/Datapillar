package com.sunny.datapillar.studio.module.workflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.common.response.ApiResponse;
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
import com.sunny.datapillar.studio.module.workflow.service.WorkflowRunBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WorkflowRunBusiness controller Responsible for workflowRunBusiness interface orchestration and
 * request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "Workflow run", description = "Workflow execution interface")
@RestController
@RequestMapping("/biz/workflows/{workflowId}")
@RequiredArgsConstructor
public class WorkflowRunBizController {

  private static final int DEFAULT_LIMIT = 20;
  private static final int DEFAULT_OFFSET = 0;
  private static final int DEFAULT_MAX_LIMIT = 200;

  private final WorkflowRunBizService workflowRunBizService;

  @Operation(summary = "Trigger workflow to run")
  @PostMapping("/runs")
  public ApiResponse<Void> trigger(
      @PathVariable Long workflowId,
      @RequestBody(required = false) WorkflowTriggerRequest request) {
    workflowRunBizService.triggerWorkflow(workflowId, request);
    return ApiResponse.ok();
  }

  @Operation(summary = "Get run list")
  @GetMapping("/runs")
  public ApiResponse<JsonNode> getRuns(
      @PathVariable Long workflowId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Integer offset,
      @RequestParam(required = false) Integer maxLimit,
      @RequestParam(required = false) String state) {
    int resolvedMaxLimit = maxLimit == null || maxLimit <= 0 ? DEFAULT_MAX_LIMIT : maxLimit;
    int resolvedLimit =
        limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, resolvedMaxLimit);
    int resolvedOffset = offset == null || offset < 0 ? DEFAULT_OFFSET : offset;
    return ApiResponse.ok(
        workflowRunBizService.getWorkflowRuns(workflowId, resolvedLimit, resolvedOffset, state));
  }

  @Operation(summary = "Get run details")
  @GetMapping("/runs/{runId}")
  public ApiResponse<JsonNode> getRun(@PathVariable Long workflowId, @PathVariable String runId) {
    return ApiResponse.ok(workflowRunBizService.getWorkflowRun(workflowId, runId));
  }

  @Operation(summary = "Get task list")
  @GetMapping("/runs/{runId}/jobs")
  public ApiResponse<JsonNode> getJobs(@PathVariable Long workflowId, @PathVariable String runId) {
    return ApiResponse.ok(workflowRunBizService.getRunJobs(workflowId, runId));
  }

  @Operation(summary = "Get task details")
  @GetMapping("/runs/{runId}/jobs/{jobId}")
  public ApiResponse<JsonNode> getJob(
      @PathVariable Long workflowId, @PathVariable String runId, @PathVariable String jobId) {
    return ApiResponse.ok(workflowRunBizService.getRunJob(workflowId, runId, jobId));
  }

  @Operation(summary = "Get task log")
  @GetMapping("/runs/{runId}/jobs/{jobId}/logs")
  public ApiResponse<JsonNode> getJobLogs(
      @PathVariable Long workflowId,
      @PathVariable String runId,
      @PathVariable String jobId,
      @RequestParam(defaultValue = "-1") int tryNumber) {
    return ApiResponse.ok(workflowRunBizService.getJobLogs(workflowId, runId, jobId, tryNumber));
  }

  @Operation(summary = "rerun mission")
  @PostMapping("/runs/{runId}/jobs/{jobId}/rerun")
  public ApiResponse<JsonNode> rerunJob(
      @PathVariable Long workflowId,
      @PathVariable String runId,
      @PathVariable String jobId,
      @RequestBody(required = false) WorkflowRerunJobRequest request) {
    return ApiResponse.ok(workflowRunBizService.rerunJob(workflowId, runId, jobId, request));
  }

  @Operation(summary = "Set task status")
  @PatchMapping("/runs/{runId}/jobs/{jobId}/state")
  public ApiResponse<Void> setJobState(
      @PathVariable Long workflowId,
      @PathVariable String runId,
      @PathVariable String jobId,
      @Valid @RequestBody WorkflowSetJobStatusRequest request) {
    workflowRunBizService.setJobState(workflowId, runId, jobId, request);
    return ApiResponse.ok();
  }

  @Operation(summary = "Batch cleaning tasks")
  @PostMapping("/runs/{runId}/clear")
  public ApiResponse<JsonNode> clearJobs(
      @PathVariable Long workflowId,
      @PathVariable String runId,
      @Valid @RequestBody WorkflowClearJobsRequest request) {
    return ApiResponse.ok(workflowRunBizService.clearJobs(workflowId, runId, request));
  }
}
