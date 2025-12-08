package com.sunny.admin.module.projects.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.common.enums.GlobalSystemCode;
import com.sunny.common.exception.GlobalException;
import com.sunny.admin.module.projects.dto.*;
import com.sunny.admin.module.projects.entity.Project;
import com.sunny.admin.module.projects.enums.ProjectStatus;
import com.sunny.admin.module.projects.mapper.ProjectMapper;
import com.sunny.admin.module.projects.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    @Override
    public IPage<ProjectRespDto> getProjectPage(ProjectQueryReqDto queryDTO, Long userId) {
        Page<ProjectRespDto> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        return projectMapper.selectProjectPage(page, queryDTO, userId);
    }

    @Override
    public IPage<ProjectRespDto> getMyProjects(ProjectQueryReqDto queryDTO, Long userId, boolean isAdmin) {
        Page<ProjectRespDto> page = new Page<>(queryDTO.getPage(), queryDTO.getSize());
        return projectMapper.selectMyProjects(page, queryDTO, userId, isAdmin);
    }

    @Override
    public ProjectRespDto getProjectById(Long id, Long userId) {
        ProjectRespDto project = projectMapper.selectProjectById(id, userId);
        if (project == null) {
            throw new GlobalException(GlobalSystemCode.PROJECT_ACCESS_DENIED, id);
        }
        return project;
    }

    @Override
    @Transactional
    public ProjectRespDto createProject(ProjectCreateReqDto createDTO, Long userId) {
        Project project = new Project();
        BeanUtils.copyProperties(createDTO, project);
        
        // 设置默认值
        project.setOwnerId(userId);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setIsFavorite(false);
        project.setMemberCount(1);
        project.setLastAccessedAt(LocalDateTime.now());
        
        // 处理标签
        if (createDTO.getTags() != null && !createDTO.getTags().isEmpty()) {
            try {
                project.setTags(objectMapper.writeValueAsString(createDTO.getTags()));
            } catch (JsonProcessingException e) {
                log.error("序列化标签失败", e);
                project.setTags("[]");
            }
        } else {
            project.setTags("[]");
        }
        
        projectMapper.insert(project);
        return getProjectById(project.getId(), userId);
    }

    @Override
    @Transactional
    public ProjectRespDto updateProject(Long id, ProjectUpdateReqDto updateDTO, Long userId) {
        // 检查项目是否存在且有权限
        Project existingProject = projectMapper.selectOne(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getId, id)
                        .eq(Project::getOwnerId, userId)
        );

        if (existingProject == null) {
            throw new GlobalException(GlobalSystemCode.PROJECT_ACCESS_DENIED, id);
        }
        
        // 更新项目信息
        Project project = new Project();
        project.setId(id);
        
        if (updateDTO.getName() != null) {
            project.setName(updateDTO.getName());
        }
        if (updateDTO.getDescription() != null) {
            project.setDescription(updateDTO.getDescription());
        }
        if (updateDTO.getStatus() != null) {
            project.setStatus(updateDTO.getStatus());
        }
        if (updateDTO.getIsFavorite() != null) {
            project.setIsFavorite(updateDTO.getIsFavorite());
        }
        if (updateDTO.getIsVisible() != null) {
            project.setIsVisible(updateDTO.getIsVisible());
        }
        
        // 处理标签
        if (updateDTO.getTags() != null) {
            try {
                project.setTags(objectMapper.writeValueAsString(updateDTO.getTags()));
            } catch (JsonProcessingException e) {
                log.error("序列化标签失败", e);
            }
        }
        
        projectMapper.updateById(project);
        return getProjectById(id, userId);
    }

    @Override
    @Transactional
    public void deleteProject(Long id, Long userId) {
        // 检查项目是否存在且有权限
        Project existingProject = projectMapper.selectOne(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getId, id)
                        .eq(Project::getOwnerId, userId)
        );

        if (existingProject == null) {
            throw new GlobalException(GlobalSystemCode.PROJECT_ACCESS_DENIED, id);
        }
        
        // 逻辑删除
        projectMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void updateLastAccessTime(Long id, Long userId) {
        projectMapper.update(null,
                new LambdaUpdateWrapper<Project>()
                        .eq(Project::getId, id)
                        .eq(Project::getOwnerId, userId)
                        .set(Project::getLastAccessedAt, LocalDateTime.now())
        );
    }
}