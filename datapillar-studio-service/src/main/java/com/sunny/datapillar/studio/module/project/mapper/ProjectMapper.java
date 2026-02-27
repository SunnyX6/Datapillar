package com.sunny.datapillar.studio.module.project.mapper;

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
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.studio.module.project.entity.Project;

/**
 * 项目Mapper
 * 负责项目数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 分页查询项目列表
     */
    IPage<ProjectResponse> selectProjectPage(
            Page<ProjectResponse> page,
            @Param("query") ProjectQueryRequest query,
            @Param("userId") Long userId
    );

    /**
     * 查询我的项目列表
     */
    IPage<ProjectResponse> selectMyProjects(
            Page<ProjectResponse> page,
            @Param("query") ProjectQueryRequest query,
            @Param("userId") Long userId,
            @Param("isAdmin") boolean isAdmin
    );

    /**
     * 根据ID查询项目详情
     */
    ProjectResponse selectProjectById(
            @Param("id") Long id,
            @Param("userId") Long userId
    );
}
