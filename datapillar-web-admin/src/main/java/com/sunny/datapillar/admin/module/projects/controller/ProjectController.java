package com.sunny.datapillar.admin.module.projects.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.admin.module.projects.dto.ProjectDto;
import com.sunny.datapillar.admin.module.projects.service.ProjectService;
import com.sunny.datapillar.admin.web.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 项目管理控制器
 *
 * @author sunny
 */
@Tag(name = "项目管理", description = "项目管理相关接口")
@RestController
@RequestMapping("/users/{userId}/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "获取用户的项目列表")
    @GetMapping
    public ApiResponse<List<ProjectDto.Response>> list(@PathVariable Long userId, ProjectDto.Query query) {
        IPage<ProjectDto.Response> result = projectService.getProjectPage(query, userId);
        long size = result.getSize();
        long current = result.getCurrent();
        int limit = (int) Math.max(size, 0);
        int offset = limit == 0 ? 0 : (int) Math.max(0, (current - 1) * size);
        return ApiResponse.page(result.getRecords(), limit, offset, result.getTotal());
    }

    @Operation(summary = "获取项目详情")
    @GetMapping("/{id}")
    public ApiResponse<ProjectDto.Response> detail(@PathVariable Long userId, @PathVariable Long id) {
        ProjectDto.Response result = projectService.getProjectById(id, userId);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "创建项目")
    @PostMapping
    public ApiResponse<Long> create(@PathVariable Long userId, @Valid @RequestBody ProjectDto.Create dto) {
        Long id = projectService.createProject(dto, userId);
        return ApiResponse.ok(id);
    }

    @Operation(summary = "更新项目")
    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long userId, @PathVariable Long id, @Valid @RequestBody ProjectDto.Update dto) {
        projectService.updateProject(id, dto, userId);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "删除项目")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long userId, @PathVariable Long id) {
        projectService.deleteProject(id, userId);
        return ApiResponse.ok(null);
    }
}
