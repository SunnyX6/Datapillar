package com.sunny.datapillar.workbench.module.projects.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.workbench.module.projects.dto.ProjectDto;
import com.sunny.datapillar.workbench.module.projects.entity.Project;

/**
 * 项目 Mapper 接口
 *
 * @author sunny
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 分页查询项目列表
     */
    IPage<ProjectDto.Response> selectProjectPage(
            Page<ProjectDto.Response> page,
            @Param("query") ProjectDto.Query query,
            @Param("userId") Long userId
    );

    /**
     * 查询我的项目列表
     */
    IPage<ProjectDto.Response> selectMyProjects(
            Page<ProjectDto.Response> page,
            @Param("query") ProjectDto.Query query,
            @Param("userId") Long userId,
            @Param("isAdmin") boolean isAdmin
    );

    /**
     * 根据ID查询项目详情
     */
    ProjectDto.Response selectProjectById(
            @Param("id") Long id,
            @Param("userId") Long userId
    );
}
