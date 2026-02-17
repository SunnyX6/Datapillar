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
import com.sunny.datapillar.studio.module.workflow.service.WorkflowDependencyBizService;
import com.sunny.datapillar.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 工作流Dependency业务控制器
 * 负责工作流Dependency业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "依赖管理", description = "工作流下的任务依赖关系操作")
@RestController
@RequestMapping("/biz/projects/{projectId}/workflows/{workflowId}")
@RequiredArgsConstructor
public class WorkflowDependencyBizController {

    private final WorkflowDependencyBizService workflowDependencyBizService;

    @Operation(summary = "获取工作流下的所有依赖")
    @GetMapping("/dependencies")
    public ApiResponse<List<JobDependencyDto.Response>> list(
            @PathVariable Long projectId,
            @PathVariable Long workflowId) {
        List<JobDependencyDto.Response> result = workflowDependencyBizService.getDependenciesByWorkflowId(workflowId);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "创建依赖关系")
    @PostMapping("/dependency")
    public ApiResponse<Void> create(
            @PathVariable Long projectId,
            @PathVariable Long workflowId,
            @Valid @RequestBody JobDependencyDto.Create dto) {
        workflowDependencyBizService.createDependency(workflowId, dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除依赖关系")
    @DeleteMapping("/dependency")
    public ApiResponse<Void> delete(
            @PathVariable Long projectId,
            @PathVariable Long workflowId,
            @RequestParam Long jobId,
            @RequestParam Long parentJobId) {
        workflowDependencyBizService.deleteDependency(jobId, parentJobId);
        return ApiResponse.ok();
    }
}
