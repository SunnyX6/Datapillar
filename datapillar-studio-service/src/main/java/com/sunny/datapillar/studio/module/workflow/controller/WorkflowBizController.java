package com.sunny.datapillar.studio.module.workflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.studio.config.openapi.OpenApiPaged;
import com.sunny.datapillar.studio.module.workflow.dto.WorkflowDto;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowBizService;
import com.sunny.datapillar.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流业务控制器
 * 负责工作流业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "工作流管理", description = "工作流 CRUD 接口")
@RestController
@RequestMapping("/biz/projects/{projectId}")
@RequiredArgsConstructor
public class WorkflowBizController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_MAX_LIMIT = 200;

    private final WorkflowBizService workflowBizService;

    @OpenApiPaged
    @Operation(summary = "获取项目的工作流列表")
    @GetMapping("/workflows")
    public ApiResponse<List<WorkflowDto.ListItem>> list(
            @PathVariable Long projectId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer maxLimit,
            @RequestParam(required = false) String workflowName,
            @RequestParam(required = false) Integer status) {
        int resolvedMaxLimit = maxLimit == null || maxLimit <= 0 ? DEFAULT_MAX_LIMIT : maxLimit;
        int resolvedLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, resolvedMaxLimit);
        int resolvedOffset = offset == null || offset < 0 ? DEFAULT_OFFSET : offset;
        long current = (resolvedOffset / resolvedLimit) + 1L;
        Page<WorkflowDto.ListItem> page = Page.of(current, resolvedLimit);
        IPage<WorkflowDto.ListItem> result = workflowBizService.getWorkflowPage(page, projectId, workflowName, status);
        return ApiResponse.page(result.getRecords(), resolvedLimit, resolvedOffset, result.getTotal());
    }

    @Operation(summary = "获取工作流详情")
    @GetMapping("/workflows/{id}")
    public ApiResponse<WorkflowDto.Response> detail(
            @PathVariable Long projectId,
            @PathVariable Long id) {
        return ApiResponse.ok(workflowBizService.getWorkflowDetail(id));
    }

    @Operation(summary = "创建工作流")
    @PostMapping("/workflow")
    public ApiResponse<Void> create(
            @PathVariable Long projectId,
            @Valid @RequestBody WorkflowDto.Create dto) {
        dto.setProjectId(projectId);
        workflowBizService.createWorkflow(dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新工作流")
    @PutMapping("/workflow/{id}")
    public ApiResponse<Void> update(
            @PathVariable Long projectId,
            @PathVariable Long id,
            @Valid @RequestBody WorkflowDto.Update dto) {
        workflowBizService.updateWorkflow(id, dto);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除工作流")
    @DeleteMapping("/workflow/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long projectId,
            @PathVariable Long id) {
        workflowBizService.deleteWorkflow(id);
        return ApiResponse.ok();
    }
}
