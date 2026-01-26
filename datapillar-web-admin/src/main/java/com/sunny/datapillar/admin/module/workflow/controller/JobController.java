package com.sunny.datapillar.admin.module.workflow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sunny.datapillar.admin.module.workflow.dto.JobDto;
import com.sunny.datapillar.admin.module.workflow.service.JobService;
import com.sunny.datapillar.admin.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 任务控制器
 *
 * @author sunny
 */
@Tag(name = "任务管理", description = "工作流下的任务 CRUD 操作")
@RestController
@RequestMapping("/users/{userId}/projects/{projectId}/workflows/{workflowId}/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @Operation(summary = "获取工作流下的所有任务")
    @GetMapping
    public ApiResponse<List<JobDto.Response>> list(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long workflowId) {
        List<JobDto.Response> result = jobService.getJobsByWorkflowId(workflowId);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{id}")
    public ApiResponse<JobDto.Response> detail(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long workflowId,
            @PathVariable Long id) {
        JobDto.Response result = jobService.getJobDetail(id);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "创建任务")
    @PostMapping
    public ApiResponse<Long> create(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long workflowId,
            @Valid @RequestBody JobDto.Create dto) {
        Long id = jobService.createJob(workflowId, dto);
        return ApiResponse.ok(id);
    }

    @Operation(summary = "更新任务")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long workflowId,
            @PathVariable Long id,
            @Valid @RequestBody JobDto.Update dto) {
        jobService.updateJob(id, dto);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long workflowId,
            @PathVariable Long id) {
        jobService.deleteJob(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "批量更新任务位置")
    @PutMapping("/layout")
    public ApiResponse<Void> updateLayout(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long workflowId,
            @Valid @RequestBody JobDto.LayoutSave dto) {
        jobService.updateJobPositions(workflowId, dto);
        return ApiResponse.ok(null);
    }
}
