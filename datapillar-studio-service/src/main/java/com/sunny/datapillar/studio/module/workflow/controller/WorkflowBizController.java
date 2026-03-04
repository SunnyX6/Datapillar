package com.sunny.datapillar.studio.module.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.config.openapi.OpenApiPaged;
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
import com.sunny.datapillar.studio.module.workflow.service.WorkflowBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workflow business controller Responsible for workflow business interface orchestration and
 * request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "Workflow", description = "Workflow interface")
@RestController
@RequestMapping("/biz/workflows")
@RequiredArgsConstructor
public class WorkflowBizController {

  private static final int DEFAULT_LIMIT = 20;
  private static final int DEFAULT_OFFSET = 0;
  private static final int DEFAULT_MAX_LIMIT = 200;

  private final WorkflowBizService workflowBizService;

  @OpenApiPaged
  @Operation(summary = "Get a list of workflows for a project")
  @GetMapping
  public ApiResponse<List<WorkflowListItemResponse>> list(
      @RequestParam Long projectId,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) Integer offset,
      @RequestParam(required = false) Integer maxLimit,
      @RequestParam(required = false) String workflowName,
      @RequestParam(required = false) Integer status) {
    int resolvedMaxLimit = maxLimit == null || maxLimit <= 0 ? DEFAULT_MAX_LIMIT : maxLimit;
    int resolvedLimit =
        limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, resolvedMaxLimit);
    int resolvedOffset = offset == null || offset < 0 ? DEFAULT_OFFSET : offset;
    long current = (resolvedOffset / resolvedLimit) + 1L;
    Page<WorkflowListItemResponse> page = Page.of(current, resolvedLimit);
    IPage<WorkflowListItemResponse> result =
        workflowBizService.getWorkflowPage(page, projectId, workflowName, status);
    return ApiResponse.page(result.getRecords(), resolvedLimit, resolvedOffset, result.getTotal());
  }

  @Operation(summary = "Get workflow details")
  @GetMapping("/{workflowId}")
  public ApiResponse<WorkflowResponse> detail(@PathVariable Long workflowId) {
    return ApiResponse.ok(workflowBizService.getWorkflowDetail(workflowId));
  }

  @Operation(summary = "Create workflow")
  @PostMapping
  public ApiResponse<Void> create(@Valid @RequestBody WorkflowCreateRequest dto) {
    workflowBizService.createWorkflow(dto);
    return ApiResponse.ok();
  }

  @Operation(summary = "Update workflow")
  @PatchMapping("/{workflowId}")
  public ApiResponse<Void> update(
      @PathVariable Long workflowId, @Valid @RequestBody WorkflowUpdateRequest dto) {
    workflowBizService.updateWorkflow(workflowId, dto);
    return ApiResponse.ok();
  }

  @Operation(summary = "Delete workflow")
  @DeleteMapping("/{workflowId}")
  public ApiResponse<Void> delete(@PathVariable Long workflowId) {
    workflowBizService.deleteWorkflow(workflowId);
    return ApiResponse.ok();
  }
}
