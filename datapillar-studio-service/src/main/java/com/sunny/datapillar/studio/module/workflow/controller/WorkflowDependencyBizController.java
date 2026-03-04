package com.sunny.datapillar.studio.module.workflow.controller;

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
import com.sunny.datapillar.studio.module.workflow.service.WorkflowDependencyBizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WorkflowDependencyBusiness controller Responsible for workflowDependencyBusiness interface
 * orchestration and request processing
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "Workflow dependencies", description = "Workflow dependency interface")
@RestController
@RequestMapping("/biz/workflows/{workflowId}")
@RequiredArgsConstructor
public class WorkflowDependencyBizController {

  private final WorkflowDependencyBizService workflowDependencyBizService;

  @Operation(summary = "Get all dependencies under the workflow")
  @GetMapping("/dependencies")
  public ApiResponse<List<JobDependencyResponse>> list(@PathVariable Long workflowId) {
    List<JobDependencyResponse> result =
        workflowDependencyBizService.getDependenciesByWorkflowId(workflowId);
    return ApiResponse.ok(result);
  }

  @Operation(summary = "Create dependencies")
  @PostMapping("/dependencies")
  public ApiResponse<Void> create(
      @PathVariable Long workflowId, @Valid @RequestBody JobDependencyCreateRequest dto) {
    workflowDependencyBizService.createDependency(workflowId, dto);
    return ApiResponse.ok();
  }

  @Operation(summary = "Remove dependencies")
  @DeleteMapping("/dependencies")
  public ApiResponse<Void> delete(
      @PathVariable Long workflowId, @RequestParam Long jobId, @RequestParam Long parentJobId) {
    workflowDependencyBizService.deleteDependency(workflowId, jobId, parentJobId);
    return ApiResponse.ok();
  }
}
