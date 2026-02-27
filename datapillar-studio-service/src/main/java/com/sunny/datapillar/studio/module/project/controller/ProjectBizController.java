package com.sunny.datapillar.studio.module.project.controller;

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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.studio.module.project.service.ProjectBizService;
import com.sunny.datapillar.studio.config.openapi.OpenApiPaged;
import com.sunny.datapillar.common.response.ApiResponse;
import com.sunny.datapillar.studio.util.UserContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目业务控制器
 * 负责项目业务接口编排与请求处理
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Tag(name = "项目", description = "项目接口")
@RestController
@RequestMapping("/biz/users/me/projects")
@RequiredArgsConstructor
public class ProjectBizController {

    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_MAX_LIMIT = 200;

    private final ProjectBizService projectBizService;

    @OpenApiPaged
    @Operation(summary = "获取用户的项目列表")
    @GetMapping
    public ApiResponse<List<ProjectResponse>> list(ProjectQueryRequest query) {
        int resolvedMaxLimit = query.getMaxLimit() == null || query.getMaxLimit() <= 0
                ? DEFAULT_MAX_LIMIT
                : query.getMaxLimit();
        int resolvedLimit = query.getLimit() == null || query.getLimit() <= 0
                ? DEFAULT_LIMIT
                : Math.min(query.getLimit(), resolvedMaxLimit);
        int resolvedOffset = query.getOffset() == null || query.getOffset() < 0 ? DEFAULT_OFFSET : query.getOffset();
        query.setLimit(resolvedLimit);
        query.setOffset(resolvedOffset);
        Long userId = UserContextUtil.getRequiredUserId();
        IPage<ProjectResponse> result = projectBizService.getProjectPage(query, userId);
        return ApiResponse.page(result.getRecords(), resolvedLimit, resolvedOffset, result.getTotal());
    }

    @Operation(summary = "获取项目详情")
    @GetMapping("/{id}")
    public ApiResponse<ProjectResponse> detail(@PathVariable Long id) {
        Long userId = UserContextUtil.getRequiredUserId();
        return ApiResponse.ok(projectBizService.getProjectById(id, userId));
    }

    @Operation(summary = "创建项目")
    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody ProjectCreateRequest dto) {
        Long userId = UserContextUtil.getRequiredUserId();
        projectBizService.createProject(dto, userId);
        return ApiResponse.ok();
    }

    @Operation(summary = "更新项目")
    @PatchMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ProjectUpdateRequest dto) {
        Long userId = UserContextUtil.getRequiredUserId();
        projectBizService.updateProject(id, dto, userId);
        return ApiResponse.ok();
    }

    @Operation(summary = "删除项目")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = UserContextUtil.getRequiredUserId();
        projectBizService.deleteProject(id, userId);
        return ApiResponse.ok();
    }
}
