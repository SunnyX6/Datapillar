package com.sunny.datapillar.studio.module.project.service.impl;

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
    public IPage<ProjectResponse> getProjectPage(ProjectQueryRequest query, Long userId) {
        return projectService.getProjectPage(query, userId);
    }

    @Override
    public ProjectResponse getProjectById(Long id, Long userId) {
        return projectService.getProjectById(id, userId);
    }

    @Override
    public Long createProject(ProjectCreateRequest dto, Long userId) {
        return projectService.createProject(dto, userId);
    }

    @Override
    public void updateProject(Long id, ProjectUpdateRequest dto, Long userId) {
        projectService.updateProject(id, dto, userId);
    }

    @Override
    public void deleteProject(Long id, Long userId) {
        projectService.deleteProject(id, userId);
    }
}
