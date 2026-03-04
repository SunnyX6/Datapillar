package com.sunny.datapillar.studio.module.workflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowDagBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WorkflowDAGBusiness controller Responsible for workflowDAGBusiness interface orchestration and
 * request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "WorkflowDAG", description = "WorkflowDAGinterface")
@RestController
@RequestMapping("/biz/workflows/{workflowId}")
@RequiredArgsConstructor
public class WorkflowDagBizController {

  private static final int DEFAULT_LIMIT = 20;
  private static final int DEFAULT_OFFSET = 0;
  private static final int DEFAULT_MAX_LIMIT = 200;

  private final WorkflowDagBizService workflowDagBizService;

  @Operation(summary = "Publishing workflow")
  @PostMapping("/publish")
  public ApiResponse<Map<String, Object>> publish(@PathVariable Long workflowId) {
    workflowDagBizService.publishWorkflow(workflowId);
    return ApiResponse.ok(Map.of());
  }

  @Operation(summary = "Pause workflow")
  @PostMapping("/pause")
  public ApiResponse<Map<String, Object>> pause(@PathVariable Long workflowId) {
    workflowDagBizService.pauseWorkflow(workflowId);
    return ApiResponse.ok(Map.of());
  }

  @Operation(summary = "Recovery workflow")
  @PostMapping("/resume")
  public ApiResponse<Map<String, Object>> resume(@PathVariable Long workflowId) {
    workflowDagBizService.resumeWorkflow(workflowId);
    return ApiResponse.ok(Map.of());
  }

  @Operation(summary = "Get DAG Details")
  @GetMapping("/dag")
  public ApiResponse<JsonNode> getDagDetail(@PathVariable Long workflowId) {
    return ApiResponse.ok(workflowDagBizService.getDagDetail(workflowId));
  }

  @Operation(summary = "Get DAG Version list")
  @GetMapping("/dag/versions")
  public ApiResponse<JsonNode> getDagVersions(
      @PathVariable Long workflowId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Integer offset,
      @RequestParam(required = false) Integer maxLimit) {
    int resolvedMaxLimit = maxLimit == null || maxLimit <= 0 ? DEFAULT_MAX_LIMIT : maxLimit;
    int resolvedLimit =
        limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, resolvedMaxLimit);
    int resolvedOffset = offset == null || offset < 0 ? DEFAULT_OFFSET : offset;
    return ApiResponse.ok(
        workflowDagBizService.getDagVersions(workflowId, resolvedLimit, resolvedOffset));
  }

  @Operation(summary = "Get DAG Version details")
  @GetMapping("/dag/versions/{versionNumber}")
  public ApiResponse<JsonNode> getDagVersion(
      @PathVariable Long workflowId, @PathVariable int versionNumber) {
    return ApiResponse.ok(workflowDagBizService.getDagVersion(workflowId, versionNumber));
  }
}
