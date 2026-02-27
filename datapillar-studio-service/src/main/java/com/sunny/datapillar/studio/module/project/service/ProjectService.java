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
    IPage<ProjectResponse> getProjectPage(ProjectQueryRequest query, Long userId);

    /**
     * 获取我的项目列表
     */
    IPage<ProjectResponse> getMyProjects(ProjectQueryRequest query, Long userId, boolean isAdmin);

    /**
     * 根据ID查询项目详情
     */
    ProjectResponse getProjectById(Long id, Long userId);

    /**
     * 创建项目
     */
    Long createProject(ProjectCreateRequest dto, Long userId);

    /**
     * 更新项目
     */
    void updateProject(Long id, ProjectUpdateRequest dto, Long userId);

    /**
     * 删除项目
     */
    void deleteProject(Long id, Long userId);

    /**
     * 更新项目最后访问时间
     */
    void updateLastAccessTime(Long id, Long userId);
}
