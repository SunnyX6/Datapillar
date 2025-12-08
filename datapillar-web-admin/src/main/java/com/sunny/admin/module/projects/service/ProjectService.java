package com.sunny.admin.module.projects.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.admin.module.projects.dto.*;

/**
 * 项目服务接口
 */
public interface ProjectService {

    /**
     * 分页查询项目列表
     *
     * @param queryDTO 查询条件
     * @param userId 当前用户ID
     * @return 项目列表
     */
    IPage<ProjectRespDto> getProjectPage(ProjectQueryReqDto queryDTO, Long userId);

    /**
     * 获取我的项目列表
     * 普通用户只能看到自己的项目，管理员可以看到所有项目
     *
     * @param queryDTO 查询条件
     * @param userId 当前用户ID
     * @param isAdmin 是否为管理员
     * @return 项目列表
     */
    IPage<ProjectRespDto> getMyProjects(ProjectQueryReqDto queryDTO, Long userId, boolean isAdmin);

    /**
     * 根据ID查询项目详情
     *
     * @param id 项目ID
     * @param userId 当前用户ID
     * @return 项目详情
     */
    ProjectRespDto getProjectById(Long id, Long userId);

    /**
     * 创建项目
     *
     * @param createDTO 创建项目请求
     * @param userId 当前用户ID
     * @return 创建的项目信息
     */
    ProjectRespDto createProject(ProjectCreateReqDto createDTO, Long userId);

    /**
     * 更新项目
     *
     * @param id 项目ID
     * @param updateDTO 更新项目请求
     * @param userId 当前用户ID
     * @return 更新后的项目信息
     */
    ProjectRespDto updateProject(Long id, ProjectUpdateReqDto updateDTO, Long userId);

    /**
     * 删除项目
     *
     * @param id 项目ID
     * @param userId 当前用户ID
     */
    void deleteProject(Long id, Long userId);

    /**
     * 更新项目最后访问时间
     *
     * @param id 项目ID
     * @param userId 当前用户ID
     */
    void updateLastAccessTime(Long id, Long userId);
}