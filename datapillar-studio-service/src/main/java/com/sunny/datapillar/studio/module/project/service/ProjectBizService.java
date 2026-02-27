package com.sunny.datapillar.studio.module.project.service;

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

/**
 * 项目业务服务
 * 提供项目业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface ProjectBizService {

    IPage<ProjectResponse> getProjectPage(ProjectQueryRequest query, Long userId);

    ProjectResponse getProjectById(Long id, Long userId);

    Long createProject(ProjectCreateRequest dto, Long userId);

    void updateProject(Long id, ProjectUpdateRequest dto, Long userId);

    void deleteProject(Long id, Long userId);
}
