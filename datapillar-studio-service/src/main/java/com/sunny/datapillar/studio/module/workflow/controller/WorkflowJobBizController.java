package com.sunny.datapillar.studio.module.workflow.controller;

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
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sunny.datapillar.studio.module.workflow.service.WorkflowJobBizService;
import com.sunny.datapillar.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 工作流任务业务控制器
 * 负责工作流任务业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "工作流任务", description = "工作流任务接口")
@RestController
@RequestMapping("/biz/workflows/{workflowId}")
@RequiredArgsConstructor
public class WorkflowJobBizController {

    private final WorkflowJobBizService workflowJobBizService;

    @Operation(summary = "获取工作流下的所有任务")
    @GetMapping("/jobs")
    public ApiResponse<List<JobResponse>> list(@PathVariable Long workflowId) {
        List<JobResponse> result = workflowJobBizService.getJobsByWorkflowId(workflowId);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/jobs/{id}")
    public ApiResponse<JobResponse> detail(
            @PathVariable Long workflowId,
            @PathVariable Long id) {
        JobResponse result = workflowJobBizService.getJobDetail(workflowId, id);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "创建任务")
    @PostMapping("/jobs")
    public ApiResponse<Void> create(
            @PathVariable Long workflowId,
            @Valid @RequestBody JobCreateRequest dto) {
        workflowJobBizService.createJob(workflowId, dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新任务")
    @PatchMapping("/jobs/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long workflowId,
            @PathVariable Long id,
            @Valid @RequestBody JobUpdateRequest dto) {
        workflowJobBizService.updateJob(workflowId, id, dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/jobs/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long workflowId,
            @PathVariable Long id) {
        workflowJobBizService.deleteJob(workflowId, id);
        return ApiResponse.ok();
    }

    @Operation(summary = "批量更新任务位置")
    @PutMapping("/jobs/layout")
    public ApiResponse<Void> updateLayout(
            @PathVariable Long workflowId,
            @Valid @RequestBody JobLayoutSaveRequest dto) {
        workflowJobBizService.updateJobPositions(workflowId, dto);
        return ApiResponse.ok();
    }
}
