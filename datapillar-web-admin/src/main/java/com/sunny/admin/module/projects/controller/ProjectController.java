package com.sunny.admin.module.projects.controller;

import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.common.response.ApiResponse;
import com.sunny.admin.security.SecurityUtil;
import com.sunny.admin.module.projects.dto.ProjectCreateReqDto;
import com.sunny.admin.module.projects.dto.ProjectQueryReqDto;
import com.sunny.admin.module.projects.dto.ProjectRespDto;
import com.sunny.admin.module.projects.dto.ProjectUpdateReqDto;
import com.sunny.admin.module.projects.service.ProjectService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 项目管理控制器
 *
 * @author sunny
 * @since 2024-01-01
 */
@Tag(name = "项目管理", description = "项目管理相关接口")
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final SecurityUtil securityUtil;

    @Operation(summary = "分页查询项目列表", description = "分页查询项目列表，支持多种查询条件")
    @GetMapping
    public ApiResponse<IPage<ProjectRespDto>> getProjectPage(ProjectQueryReqDto queryDTO) {
        Long userId = securityUtil.getCurrentUserId();
        IPage<ProjectRespDto> result = projectService.getProjectPage(queryDTO, userId);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "获取我的项目列表", description = "普通用户只能看到自己的项目，管理员可以看到所有项目")
    @GetMapping("/my")
    public ApiResponse<IPage<ProjectRespDto>> getMyProjects(ProjectQueryReqDto queryDTO) {
        Long userId = securityUtil.getCurrentUserId();
        boolean isAdmin = securityUtil.isCurrentUserAdmin();
        IPage<ProjectRespDto> result = projectService.getMyProjects(queryDTO, userId, isAdmin);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "根据ID查询项目详情", description = "根据项目ID获取项目的详细信息")
    @GetMapping("/{id}")
    public ApiResponse<ProjectRespDto> getProjectById(@PathVariable Long id) {
        Long userId = securityUtil.getCurrentUserId();
        ProjectRespDto result = projectService.getProjectById(id, userId);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "创建项目", description = "创建一个新的项目")
    @PostMapping
    public ApiResponse<ProjectRespDto> createProject(@Valid @RequestBody ProjectCreateReqDto createDTO) {
        Long userId = securityUtil.getCurrentUserId();
        ProjectRespDto result = projectService.createProject(createDTO, userId);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "更新项目", description = "更新项目的基本信息")
    @PostMapping("/update/{id}")
    public ApiResponse<ProjectRespDto> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody ProjectUpdateReqDto updateDTO) {
        Long userId = securityUtil.getCurrentUserId();
        ProjectRespDto result = projectService.updateProject(id, updateDTO, userId);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "删除项目", description = "删除指定的项目")
    @PostMapping("/delete/{id}")
    public ApiResponse<Void> deleteProject(@PathVariable Long id) {
        Long userId = securityUtil.getCurrentUserId();
        projectService.deleteProject(id, userId);
        return ApiResponse.ok(null);
    }

}