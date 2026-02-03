package com.sunny.datapillar.studio.module.workflow.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sunny.datapillar.studio.module.workflow.dto.JobDependencyDto;
import com.sunny.datapillar.studio.module.workflow.service.JobDependencyService;
import com.sunny.datapillar.studio.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 任务依赖控制器
 *
 * @author sunny
 */
@Tag(name = "依赖管理", description = "工作流下的任务依赖关系操作")
@RestController
@RequestMapping("/users/{userId}/projects/{projectId}/workflows/{workflowId}/dependencies")
@RequiredArgsConstructor
public class JobDependencyController {

    private final JobDependencyService dependencyService;

    @Operation(summary = "获取工作流下的所有依赖")
    @GetMapping
    public ApiResponse<List<JobDependencyDto.Response>> list(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long workflowId) {
        List<JobDependencyDto.Response> result = dependencyService.getDependenciesByWorkflowId(workflowId);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "创建依赖关系")
    @PostMapping
    public ApiResponse<Long> create(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long workflowId,
            @Valid @RequestBody JobDependencyDto.Create dto) {
        Long id = dependencyService.createDependency(workflowId, dto);
        return ApiResponse.ok(id);
    }

    @Operation(summary = "删除依赖关系")
    @DeleteMapping
    public ApiResponse<Void> delete(
            @PathVariable Long userId,
            @PathVariable Long projectId,
            @PathVariable Long workflowId,
            @RequestParam Long jobId,
            @RequestParam Long parentJobId) {
        dependencyService.deleteDependency(jobId, parentJobId);
        return ApiResponse.ok(null);
    }
}
