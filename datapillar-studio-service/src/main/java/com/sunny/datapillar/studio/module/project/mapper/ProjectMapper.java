package com.sunny.datapillar.studio.module.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.sunny.datapillar.studio.module.project.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ProjectMapper Responsible for project data access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

  /** Paging query item list */
  IPage<ProjectResponse> selectProjectPage(
      Page<ProjectResponse> page,
      @Param("query") ProjectQueryRequest query,
      @Param("userId") Long userId);

  /** Check my project list */
  IPage<ProjectResponse> selectMyProjects(
      Page<ProjectResponse> page,
      @Param("query") ProjectQueryRequest query,
      @Param("userId") Long userId,
      @Param("isAdmin") boolean isAdmin);

  /** According toIDQuery project details */
  ProjectResponse selectProjectById(@Param("id") Long id, @Param("userId") Long userId);
}
