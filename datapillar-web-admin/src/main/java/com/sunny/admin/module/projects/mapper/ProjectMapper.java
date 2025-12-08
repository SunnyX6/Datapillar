package com.sunny.admin.module.projects.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.admin.module.projects.dto.ProjectQueryReqDto;
import com.sunny.admin.module.projects.dto.ProjectRespDto;
import com.sunny.admin.module.projects.entity.Project;
import org.apache.ibatis.annotations.*;

/**
 * 项目 Mapper 接口
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /**
     * 分页查询项目列表
     */
    @Select("SELECT " +
            "p.id, p.name, p.description, p.owner_id, u.username as owner_name, " +
            "p.status, p.tags, p.is_favorite, p.is_visible, p.member_count, " +
            "p.last_accessed_at, p.created_at, p.updated_at " +
            "FROM projects p " +
            "LEFT JOIN users u ON p.owner_id = u.id " +
            "WHERE p.deleted = false " +
            "ORDER BY p.updated_at DESC")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "name", column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "ownerId", column = "owner_id"),
            @Result(property = "ownerName", column = "owner_name"),
            @Result(property = "status", column = "status"),
            @Result(property = "isFavorite", column = "is_favorite"),
            @Result(property = "isVisible", column = "is_visible"),
            @Result(property = "memberCount", column = "member_count"),
            @Result(property = "lastAccessedAt", column = "last_accessed_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    IPage<ProjectRespDto> selectProjectPage(
            Page<ProjectRespDto> page,
            @Param("query") ProjectQueryReqDto queryDTO,
            @Param("userId") Long userId
    );

    /**
     * 查询我的项目列表
     * 普通用户只能看到自己的项目，管理员可以看到所有项目
     */
    @Select("SELECT " +
            "p.id, p.name, p.description, p.owner_id, u.username as owner_name, " +
            "p.status, p.tags, p.is_favorite, p.is_visible, p.member_count, " +
            "p.last_accessed_at, p.created_at, p.updated_at " +
            "FROM projects p " +
            "LEFT JOIN users u ON p.owner_id = u.id " +
            "WHERE p.deleted = false " +
            "AND (#{isAdmin} = true OR p.owner_id = #{userId}) " +
            "ORDER BY p.updated_at DESC")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "name", column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "ownerId", column = "owner_id"),
            @Result(property = "ownerName", column = "owner_name"),
            @Result(property = "status", column = "status"),
            @Result(property = "isFavorite", column = "is_favorite"),
            @Result(property = "isVisible", column = "is_visible"),
            @Result(property = "memberCount", column = "member_count"),
            @Result(property = "lastAccessedAt", column = "last_accessed_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    IPage<ProjectRespDto> selectMyProjects(
            Page<ProjectRespDto> page,
            @Param("query") ProjectQueryReqDto queryDTO,
            @Param("userId") Long userId,
            @Param("isAdmin") boolean isAdmin
    );

    /**
     * 根据ID查询项目详情
     */
    @Select("SELECT " +
            "p.id, p.name, p.description, p.owner_id, u.username as owner_name, " +
            "p.status, p.tags, p.is_favorite, p.is_visible, p.member_count, " +
            "p.last_accessed_at, p.created_at, p.updated_at " +
            "FROM projects p " +
            "LEFT JOIN users u ON p.owner_id = u.id " +
            "WHERE p.id = #{id} AND p.deleted = false")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "name", column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "ownerId", column = "owner_id"),
            @Result(property = "ownerName", column = "owner_name"),
            @Result(property = "status", column = "status"),
            @Result(property = "isFavorite", column = "is_favorite"),
            @Result(property = "isVisible", column = "is_visible"),
            @Result(property = "memberCount", column = "member_count"),
            @Result(property = "lastAccessedAt", column = "last_accessed_at"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    ProjectRespDto selectProjectById(
            @Param("id") Long id,
            @Param("userId") Long userId
    );
}