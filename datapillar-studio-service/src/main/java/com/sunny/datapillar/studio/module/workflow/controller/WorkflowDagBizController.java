package com.sunny.datapillar.studio.module.workflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowDagBizService;
import com.sunny.datapillar.common.response.ApiResponse;
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
 * 工作流DAG业务控制器
 * 负责工作流DAG业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "工作流DAG", description = "工作流DAG接口")
@RestController
@RequestMapping("/biz/workflows/{workflowId}")
@RequiredArgsConstructor
public class WorkflowDagBizController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_MAX_LIMIT = 200;

    private final WorkflowDagBizService workflowDagBizService;

    @Operation(summary = "发布工作流")
    @PostMapping("/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable Long workflowId) {
        workflowDagBizService.publishWorkflow(workflowId);
        return ApiResponse.ok(Map.of());
    }

    @Operation(summary = "暂停工作流")
    @PostMapping("/pause")
    public ApiResponse<Map<String, Object>> pause(@PathVariable Long workflowId) {
        workflowDagBizService.pauseWorkflow(workflowId);
        return ApiResponse.ok(Map.of());
    }

    @Operation(summary = "恢复工作流")
    @PostMapping("/resume")
    public ApiResponse<Map<String, Object>> resume(@PathVariable Long workflowId) {
        workflowDagBizService.resumeWorkflow(workflowId);
        return ApiResponse.ok(Map.of());
    }

    @Operation(summary = "获取 DAG 详情")
    @GetMapping("/dag")
    public ApiResponse<JsonNode> getDagDetail(@PathVariable Long workflowId) {
        return ApiResponse.ok(workflowDagBizService.getDagDetail(workflowId));
    }

    @Operation(summary = "获取 DAG 版本列表")
    @GetMapping("/dag/versions")
    public ApiResponse<JsonNode> getDagVersions(@PathVariable Long workflowId,
                                                @RequestParam(required = false) Integer limit,
                                                @RequestParam(required = false) Integer offset,
                                                @RequestParam(required = false) Integer maxLimit) {
        int resolvedMaxLimit = maxLimit == null || maxLimit <= 0 ? DEFAULT_MAX_LIMIT : maxLimit;
        int resolvedLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, resolvedMaxLimit);
        int resolvedOffset = offset == null || offset < 0 ? DEFAULT_OFFSET : offset;
        return ApiResponse.ok(workflowDagBizService.getDagVersions(workflowId, resolvedLimit, resolvedOffset));
    }

    @Operation(summary = "获取 DAG 版本详情")
    @GetMapping("/dag/versions/{versionNumber}")
    public ApiResponse<JsonNode> getDagVersion(@PathVariable Long workflowId,
                                               @PathVariable int versionNumber) {
        return ApiResponse.ok(workflowDagBizService.getDagVersion(workflowId, versionNumber));
    }
}
