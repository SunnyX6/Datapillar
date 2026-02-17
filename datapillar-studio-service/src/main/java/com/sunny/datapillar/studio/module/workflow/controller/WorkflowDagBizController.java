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
@Tag(name = "工作流DAG管理", description = "工作流发布与 DAG 查询")
@RestController
@RequestMapping("/biz/projects/{projectId}/workflow/{id}")
@RequiredArgsConstructor
public class WorkflowDagBizController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_MAX_LIMIT = 200;

    private final WorkflowDagBizService workflowDagBizService;

    @Operation(summary = "发布工作流")
    @PostMapping("/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable Long projectId, @PathVariable Long id) {
        workflowDagBizService.publishWorkflow(id);
        return ApiResponse.ok(Map.of());
    }

    @Operation(summary = "暂停工作流")
    @PostMapping("/pause")
    public ApiResponse<Map<String, Object>> pause(@PathVariable Long projectId, @PathVariable Long id) {
        workflowDagBizService.pauseWorkflow(id);
        return ApiResponse.ok(Map.of());
    }

    @Operation(summary = "恢复工作流")
    @PostMapping("/resume")
    public ApiResponse<Map<String, Object>> resume(@PathVariable Long projectId, @PathVariable Long id) {
        workflowDagBizService.resumeWorkflow(id);
        return ApiResponse.ok(Map.of());
    }

    @Operation(summary = "获取 DAG 详情")
    @GetMapping("/dag")
    public ApiResponse<JsonNode> getDagDetail(@PathVariable Long projectId, @PathVariable Long id) {
        return ApiResponse.ok(workflowDagBizService.getDagDetail(id));
    }

    @Operation(summary = "获取 DAG 版本列表")
    @GetMapping("/dag/versions")
    public ApiResponse<JsonNode> getDagVersions(@PathVariable Long projectId,
                                                @PathVariable Long id,
                                                @RequestParam(required = false) Integer limit,
                                                @RequestParam(required = false) Integer offset,
                                                @RequestParam(required = false) Integer maxLimit) {
        int resolvedMaxLimit = maxLimit == null || maxLimit <= 0 ? DEFAULT_MAX_LIMIT : maxLimit;
        int resolvedLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, resolvedMaxLimit);
        int resolvedOffset = offset == null || offset < 0 ? DEFAULT_OFFSET : offset;
        return ApiResponse.ok(workflowDagBizService.getDagVersions(id, resolvedLimit, resolvedOffset));
    }

    @Operation(summary = "获取 DAG 版本详情")
    @GetMapping("/dag/versions/{versionNumber}")
    public ApiResponse<JsonNode> getDagVersion(@PathVariable Long projectId,
                                               @PathVariable Long id,
                                               @PathVariable int versionNumber) {
        return ApiResponse.ok(workflowDagBizService.getDagVersion(id, versionNumber));
    }
}
