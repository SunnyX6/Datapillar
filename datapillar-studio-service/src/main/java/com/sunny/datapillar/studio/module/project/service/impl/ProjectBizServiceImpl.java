package com.sunny.datapillar.studio.module.project.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.studio.module.project.dto.ProjectDto;
import com.sunny.datapillar.studio.module.project.service.ProjectBizService;
import com.sunny.datapillar.studio.module.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 项目业务服务实现
 * 实现项目业务业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class ProjectBizServiceImpl implements ProjectBizService {

    private final ProjectService projectService;

    @Override
    public IPage<ProjectDto.Response> getProjectPage(ProjectDto.Query query, Long userId) {
        return projectService.getProjectPage(query, userId);
    }

    @Override
    public ProjectDto.Response getProjectById(Long id, Long userId) {
        return projectService.getProjectById(id, userId);
    }

    @Override
    public Long createProject(ProjectDto.Create dto, Long userId) {
        return projectService.createProject(dto, userId);
    }

    @Override
    public void updateProject(Long id, ProjectDto.Update dto, Long userId) {
        projectService.updateProject(id, dto, userId);
    }

    @Override
    public void deleteProject(Long id, Long userId) {
        projectService.deleteProject(id, userId);
    }
}
