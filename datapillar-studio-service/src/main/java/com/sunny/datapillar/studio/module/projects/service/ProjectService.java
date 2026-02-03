package com.sunny.datapillar.studio.module.projects.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.studio.module.projects.dto.ProjectDto;

/**
 * 项目服务接口
 *
 * @author sunny
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
