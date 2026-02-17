package com.sunny.datapillar.studio.module.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.studio.module.project.dto.ProjectDto;

/**
 * 项目服务
 * 提供项目业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface ProjectService {

    /**
     * 分页查询项目列表
     */
    IPage<ProjectDto.Response> getProjectPage(ProjectDto.Query query, Long userId);

    /**
     * 获取我的项目列表
     */
    IPage<ProjectDto.Response> getMyProjects(ProjectDto.Query query, Long userId, boolean isAdmin);

    /**
     * 根据ID查询项目详情
     */
    ProjectDto.Response getProjectById(Long id, Long userId);

    /**
     * 创建项目
     */
    Long createProject(ProjectDto.Create dto, Long userId);

    /**
     * 更新项目
     */
    void updateProject(Long id, ProjectDto.Update dto, Long userId);

    /**
     * 删除项目
     */
    void deleteProject(Long id, Long userId);

    /**
     * 更新项目最后访问时间
     */
    void updateLastAccessTime(Long id, Long userId);
}
