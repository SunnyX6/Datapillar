package com.sunny.datapillar.studio.module.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.studio.module.project.dto.ProjectDto;

/**
 * 项目业务服务
 * 提供项目业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface ProjectBizService {

    IPage<ProjectDto.Response> getProjectPage(ProjectDto.Query query, Long userId);

    ProjectDto.Response getProjectById(Long id, Long userId);

    Long createProject(ProjectDto.Create dto, Long userId);

    void updateProject(Long id, ProjectDto.Update dto, Long userId);

    void deleteProject(Long id, Long userId);
}
