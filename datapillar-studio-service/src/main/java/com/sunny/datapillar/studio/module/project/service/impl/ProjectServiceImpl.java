package com.sunny.datapillar.studio.module.project.service.impl;

import java.time.LocalDateTime;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.module.project.dto.ProjectDto;
import com.sunny.datapillar.studio.module.project.entity.Project;
import com.sunny.datapillar.studio.module.project.enums.ProjectStatus;
import com.sunny.datapillar.studio.module.project.mapper.ProjectMapper;
import com.sunny.datapillar.studio.module.project.service.ProjectService;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.InternalException;

/**
 * 项目服务实现
 * 实现项目业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int DEFAULT_OFFSET = 0;
    private static final int DEFAULT_MAX_LIMIT = 200;

    private final ProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    @Override
    public IPage<ProjectDto.Response> getProjectPage(ProjectDto.Query query, Long userId) {
        int resolvedMaxLimit = query.getMaxLimit() == null || query.getMaxLimit() <= 0
                ? DEFAULT_MAX_LIMIT
                : query.getMaxLimit();
        int resolvedLimit = query.getLimit() == null || query.getLimit() <= 0
                ? DEFAULT_LIMIT
                : Math.min(query.getLimit(), resolvedMaxLimit);
        int resolvedOffset = query.getOffset() == null || query.getOffset() < 0 ? DEFAULT_OFFSET : query.getOffset();
        Page<ProjectDto.Response> page = Page.of((resolvedOffset / resolvedLimit) + 1L, resolvedLimit);
        return projectMapper.selectProjectPage(page, query, userId);
    }

    @Override
    public IPage<ProjectDto.Response> getMyProjects(ProjectDto.Query query, Long userId, boolean isAdmin) {
        int resolvedMaxLimit = query.getMaxLimit() == null || query.getMaxLimit() <= 0
                ? DEFAULT_MAX_LIMIT
                : query.getMaxLimit();
        int resolvedLimit = query.getLimit() == null || query.getLimit() <= 0
                ? DEFAULT_LIMIT
                : Math.min(query.getLimit(), resolvedMaxLimit);
        int resolvedOffset = query.getOffset() == null || query.getOffset() < 0 ? DEFAULT_OFFSET : query.getOffset();
        Page<ProjectDto.Response> page = Page.of((resolvedOffset / resolvedLimit) + 1L, resolvedLimit);
        return projectMapper.selectMyProjects(page, query, userId, isAdmin);
    }

    @Override
    public ProjectDto.Response getProjectById(Long id, Long userId) {
        ProjectDto.Response project = projectMapper.selectProjectById(id, userId);
        if (project == null) {
            throw new ForbiddenException("无权限访问该项目: projectId=%s", id);
        }
        return project;
    }

    @Override
    @Transactional
    public Long createProject(ProjectDto.Create dto, Long userId) {
        Project project = new Project();
        BeanUtils.copyProperties(dto, project);

        project.setOwnerId(userId);
        project.setStatus(ProjectStatus.ACTIVE);
        project.setIsFavorite(false);
        project.setMemberCount(1);
        project.setLastAccessedAt(LocalDateTime.now());

        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            try {
                project.setTags(objectMapper.writeValueAsString(dto.getTags()));
            } catch (JsonProcessingException e) {
                log.error("序列化标签失败", e);
                throw new InternalException(e, "服务器内部错误");
            }
        } else {
            project.setTags("[]");
        }

        projectMapper.insert(project);
        log.info("Created project: id={}, name={}", project.getId(), project.getName());
        return project.getId();
    }

    @Override
    @Transactional
    public void updateProject(Long id, ProjectDto.Update dto, Long userId) {
        Project existingProject = projectMapper.selectOne(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getId, id)
                        .eq(Project::getOwnerId, userId)
        );

        if (existingProject == null) {
            throw new ForbiddenException("无权限访问该项目: projectId=%s", id);
        }

        Project project = new Project();
        project.setId(id);

        if (dto.getName() != null) {
            project.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            project.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null) {
            project.setStatus(dto.getStatus());
        }
        if (dto.getIsFavorite() != null) {
            project.setIsFavorite(dto.getIsFavorite());
        }
        if (dto.getIsVisible() != null) {
            project.setIsVisible(dto.getIsVisible());
        }

        if (dto.getTags() != null) {
            try {
                project.setTags(objectMapper.writeValueAsString(dto.getTags()));
            } catch (JsonProcessingException e) {
                log.error("序列化标签失败", e);
                throw new InternalException(e, "服务器内部错误");
            }
        }

        projectMapper.updateById(project);
        log.info("Updated project: id={}", id);
    }

    @Override
    @Transactional
    public void deleteProject(Long id, Long userId) {
        Project existingProject = projectMapper.selectOne(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getId, id)
                        .eq(Project::getOwnerId, userId)
        );

        if (existingProject == null) {
            throw new ForbiddenException("无权限访问该项目: projectId=%s", id);
        }

        projectMapper.deleteById(id);
        log.info("Deleted project: id={}", id);
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
