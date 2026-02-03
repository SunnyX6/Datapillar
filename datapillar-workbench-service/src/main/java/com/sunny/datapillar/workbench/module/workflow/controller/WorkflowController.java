package com.sunny.datapillar.workbench.module.workflow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.workbench.module.workflow.dto.WorkflowDto;
import com.sunny.datapillar.workbench.module.workflow.service.WorkflowService;
import com.sunny.datapillar.workbench.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 工作流控制器
 *
 * @author sunny
 */
@Tag(name = "工作流管理", description = "工作流 CRUD、Airflow DAG 管理和任务运行管理")
@RestController
@RequestMapping("/users/{userId}/projects/{projectId}/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    // ==================== 工作流 CRUD ====================

    @Operation(summary = "获取项目的工作流列表")
    @GetMapping
    public ApiResponse<List<WorkflowDto.ListItem>> list(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String workflowName,
            @RequestParam(required = false) Integer status) {
        Page<WorkflowDto.ListItem> page = new Page<>(pageNum, pageSize);
        IPage<WorkflowDto.ListItem> result = workflowService.getWorkflowPage(page, projectId, workflowName, status);
        long size = result.getSize();
        long current = result.getCurrent();
        int limit = (int) Math.max(size, 0);
        int offset = limit == 0 ? 0 : (int) Math.max(0, (current - 1) * size);
        return ApiResponse.page(result.getRecords(), limit, offset, result.getTotal());
    }

    @Operation(summary = "获取工作流详情")
    @GetMapping("/{id}")
    public ApiResponse<WorkflowDto.Response> detail(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id) {
        WorkflowDto.Response result = workflowService.getWorkflowDetail(id);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "创建工作流")
    @PostMapping
    public ApiResponse<Long> create(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @Valid @RequestBody WorkflowDto.Create dto) {
        dto.setProjectId(projectId);
        Long id = workflowService.createWorkflow(dto);
        return ApiResponse.ok(id);
    }

    @Operation(summary = "更新工作流")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id,
            @Valid @RequestBody WorkflowDto.Update dto) {
        workflowService.updateWorkflow(id, dto);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "删除工作流")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id) {
        workflowService.deleteWorkflow(id);
        return ApiResponse.ok(null);
    }

    // ==================== DAG 管理 ====================

    @Operation(summary = "发布工作流到 Airflow")
    @PostMapping("/{id}/publish")
    public ApiResponse<Void> publish(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id) {
        workflowService.publishWorkflow(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "暂停工作流")
    @PostMapping("/{id}/pause")
    public ApiResponse<Void> pause(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id) {
        workflowService.pauseWorkflow(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "恢复工作流")
    @PostMapping("/{id}/resume")
    public ApiResponse<Void> resume(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id) {
        workflowService.resumeWorkflow(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取DAG详情（从Airflow）")
    @GetMapping("/{id}/dag")
    public ApiResponse<JsonNode> getDagDetail(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id) {
        JsonNode result = workflowService.getDagDetail(id);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "获取DAG版本列表")
    @GetMapping("/{id}/dag/versions")
    public ApiResponse<JsonNode> getDagVersions(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        JsonNode result = workflowService.getDagVersions(id, limit, offset);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "获取DAG特定版本详情")
    @GetMapping("/{id}/dag/versions/{versionNumber}")
    public ApiResponse<JsonNode> getDagVersion(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id,
            @PathVariable int versionNumber) {
        JsonNode result = workflowService.getDagVersion(id, versionNumber);
        return ApiResponse.ok(result);
    }

    // ==================== DAG Run 管理 ====================

    @Operation(summary = "触发工作流运行")
    @PostMapping("/{id}/runs")
    public ApiResponse<JsonNode> trigger(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id,
            @RequestBody(required = false) WorkflowDto.TriggerRequest request) {
        JsonNode result = workflowService.triggerWorkflow(id, request);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "获取运行列表")
    @GetMapping("/{id}/runs")
    public ApiResponse<JsonNode> getRuns(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String state) {
        JsonNode result = workflowService.getWorkflowRuns(id, limit, offset, state);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "获取运行详情")
    @GetMapping("/{id}/runs/{runId}")
    public ApiResponse<JsonNode> getRun(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id,
            @PathVariable String runId) {
        JsonNode result = workflowService.getWorkflowRun(id, runId);
        return ApiResponse.ok(result);
    }

    // ==================== Job 管理 ====================

    @Operation(summary = "获取任务列表")
    @GetMapping("/{id}/runs/{runId}/jobs")
    public ApiResponse<JsonNode> getJobs(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id,
            @PathVariable String runId) {
        JsonNode result = workflowService.getRunJobs(id, runId);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{id}/runs/{runId}/jobs/{jobId}")
    public ApiResponse<JsonNode> getJob(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id,
            @PathVariable String runId,
            @PathVariable String jobId) {
        JsonNode result = workflowService.getRunJob(id, runId, jobId);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "获取任务日志")
    @GetMapping("/{id}/runs/{runId}/jobs/{jobId}/logs")
    public ApiResponse<JsonNode> getJobLogs(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id,
            @PathVariable String runId,
            @PathVariable String jobId,
            @RequestParam(defaultValue = "-1") int tryNumber) {
        JsonNode result = workflowService.getJobLogs(id, runId, jobId, tryNumber);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "重跑任务")
    @PostMapping("/{id}/runs/{runId}/jobs/{jobId}/rerun")
    public ApiResponse<JsonNode> rerunJob(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id,
            @PathVariable String runId,
            @PathVariable String jobId,
            @RequestBody(required = false) WorkflowDto.RerunJobRequest request) {
        JsonNode result = workflowService.rerunJob(id, runId, jobId, request);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "设置任务状态")
    @PatchMapping("/{id}/runs/{runId}/jobs/{jobId}/state")
    public ApiResponse<JsonNode> setJobState(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id,
            @PathVariable String runId,
            @PathVariable String jobId,
            @Valid @RequestBody WorkflowDto.SetJobStateRequest request) {
        JsonNode result = workflowService.setJobState(id, runId, jobId, request);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "批量清除任务")
    @PostMapping("/{id}/runs/{runId}/clear")
    public ApiResponse<JsonNode> clearJobs(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long id,
            @PathVariable String runId,
            @Valid @RequestBody WorkflowDto.ClearJobsRequest request) {
        JsonNode result = workflowService.clearJobs(id, runId, request);
        return ApiResponse.ok(result);
    }
}
