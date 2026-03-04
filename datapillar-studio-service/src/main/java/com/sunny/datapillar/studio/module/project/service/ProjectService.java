package com.sunny.datapillar.studio.module.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
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

/**
 * Project services Provide project business capabilities and domain services
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface ProjectService {

  /** Paging query item list */
  IPage<ProjectResponse> getProjectPage(ProjectQueryRequest query, Long userId);

  /** Get my project list */
  IPage<ProjectResponse> getMyProjects(ProjectQueryRequest query, Long userId, boolean isAdmin);

  /** According toIDQuery project details */
  ProjectResponse getProjectById(Long id, Long userId);

  /** Create project */
  Long createProject(ProjectCreateRequest dto, Long userId);

  /** Update project */
  void updateProject(Long id, ProjectUpdateRequest dto, Long userId);

  /** Delete project */
  void deleteProject(Long id, Long userId);

  /** Update project last access time */
  void updateLastAccessTime(Long id, Long userId);
}
