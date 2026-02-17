package com.sunny.datapillar.studio.module.workflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.studio.module.workflow.dto.WorkflowDto;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowRunBizService;
import com.sunny.datapillar.common.response.ApiResponse;
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
 * 工作流Run业务控制器
 * 负责工作流Run业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "工作流运行管理", description = "工作流运行与运行时任务管理")
@RestController
@RequestMapping("/biz/projects/{projectId}/workflows/{id}")
@RequiredArgsConstructor
public class WorkflowRunBizController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_MAX_LIMIT = 200;

    private final WorkflowRunBizService workflowRunBizService;

    @Operation(summary = "触发工作流运行")
    @PostMapping("/run")
    public ApiResponse<Void> trigger(@PathVariable Long projectId,
                                     @PathVariable Long id,
                                     @RequestBody(required = false) WorkflowDto.TriggerRequest request) {
        workflowRunBizService.triggerWorkflow(id, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取运行列表")
    @GetMapping("/runs")
    public ApiResponse<JsonNode> getRuns(@PathVariable Long projectId,
                                         @PathVariable Long id,
                                         @RequestParam(required = false) Integer limit,
                                         @RequestParam(required = false) Integer offset,
                                         @RequestParam(required = false) Integer maxLimit,
                                         @RequestParam(required = false) String state) {
        int resolvedMaxLimit = maxLimit == null || maxLimit <= 0 ? DEFAULT_MAX_LIMIT : maxLimit;
        int resolvedLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, resolvedMaxLimit);
        int resolvedOffset = offset == null || offset < 0 ? DEFAULT_OFFSET : offset;
        return ApiResponse.ok(workflowRunBizService.getWorkflowRuns(id, resolvedLimit, resolvedOffset, state));
    }

    @Operation(summary = "获取运行详情")
    @GetMapping("/runs/{runId}")
    public ApiResponse<JsonNode> getRun(@PathVariable Long projectId,
                                        @PathVariable Long id,
                                        @PathVariable String runId) {
        return ApiResponse.ok(workflowRunBizService.getWorkflowRun(id, runId));
    }

    @Operation(summary = "获取任务列表")
    @GetMapping("/runs/{runId}/jobs")
    public ApiResponse<JsonNode> getJobs(@PathVariable Long projectId,
                                         @PathVariable Long id,
                                         @PathVariable String runId) {
        return ApiResponse.ok(workflowRunBizService.getRunJobs(id, runId));
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/runs/{runId}/jobs/{jobId}")
    public ApiResponse<JsonNode> getJob(@PathVariable Long projectId,
                                        @PathVariable Long id,
                                        @PathVariable String runId,
                                        @PathVariable String jobId) {
        return ApiResponse.ok(workflowRunBizService.getRunJob(id, runId, jobId));
    }

    @Operation(summary = "获取任务日志")
    @GetMapping("/runs/{runId}/jobs/{jobId}/logs")
    public ApiResponse<JsonNode> getJobLogs(@PathVariable Long projectId,
                                            @PathVariable Long id,
                                            @PathVariable String runId,
                                            @PathVariable String jobId,
                                            @RequestParam(defaultValue = "-1") int tryNumber) {
        return ApiResponse.ok(workflowRunBizService.getJobLogs(id, runId, jobId, tryNumber));
    }

    @Operation(summary = "重跑任务")
    @PostMapping("/runs/{runId}/jobs/{jobId}/rerun")
    public ApiResponse<JsonNode> rerunJob(@PathVariable Long projectId,
                                          @PathVariable Long id,
                                          @PathVariable String runId,
                                          @PathVariable String jobId,
                                          @RequestBody(required = false) WorkflowDto.RerunJobRequest request) {
        return ApiResponse.ok(workflowRunBizService.rerunJob(id, runId, jobId, request));
    }

    @Operation(summary = "设置任务状态")
    @PatchMapping("/runs/{runId}/job/{jobId}/state")
    public ApiResponse<Void> setJobState(@PathVariable Long projectId,
                                         @PathVariable Long id,
                                         @PathVariable String runId,
                                         @PathVariable String jobId,
                                         @Valid @RequestBody WorkflowDto.SetJobStateRequest request) {
        workflowRunBizService.setJobState(id, runId, jobId, request);
        return ApiResponse.ok();
    }

    @Operation(summary = "批量清除任务")
    @PostMapping("/runs/{runId}/clear")
    public ApiResponse<JsonNode> clearJobs(@PathVariable Long projectId,
                                           @PathVariable Long id,
                                           @PathVariable String runId,
                                           @Valid @RequestBody WorkflowDto.ClearJobsRequest request) {
        return ApiResponse.ok(workflowRunBizService.clearJobs(id, runId, request));
    }
}
