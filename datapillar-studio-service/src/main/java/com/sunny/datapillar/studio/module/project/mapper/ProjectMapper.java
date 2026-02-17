package com.sunny.datapillar.studio.module.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.studio.module.project.dto.ProjectDto;
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
