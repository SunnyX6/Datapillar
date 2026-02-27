package com.sunny.datapillar.studio.module.project.service.impl;

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
    public IPage<ProjectResponse> getProjectPage(ProjectQueryRequest query, Long userId) {
        int resolvedMaxLimit = query.getMaxLimit() == null || query.getMaxLimit() <= 0
                ? DEFAULT_MAX_LIMIT
                : query.getMaxLimit();
        int resolvedLimit = query.getLimit() == null || query.getLimit() <= 0
                ? DEFAULT_LIMIT
                : Math.min(query.getLimit(), resolvedMaxLimit);
        int resolvedOffset = query.getOffset() == null || query.getOffset() < 0 ? DEFAULT_OFFSET : query.getOffset();
        Page<ProjectResponse> page = Page.of((resolvedOffset / resolvedLimit) + 1L, resolvedLimit);
        return projectMapper.selectProjectPage(page, query, userId);
    }

    @Override
    public IPage<ProjectResponse> getMyProjects(ProjectQueryRequest query, Long userId, boolean isAdmin) {
        int resolvedMaxLimit = query.getMaxLimit() == null || query.getMaxLimit() <= 0
                ? DEFAULT_MAX_LIMIT
                : query.getMaxLimit();
        int resolvedLimit = query.getLimit() == null || query.getLimit() <= 0
                ? DEFAULT_LIMIT
                : Math.min(query.getLimit(), resolvedMaxLimit);
        int resolvedOffset = query.getOffset() == null || query.getOffset() < 0 ? DEFAULT_OFFSET : query.getOffset();
        Page<ProjectResponse> page = Page.of((resolvedOffset / resolvedLimit) + 1L, resolvedLimit);
        return projectMapper.selectMyProjects(page, query, userId, isAdmin);
    }

    @Override
    public ProjectResponse getProjectById(Long id, Long userId) {
        ProjectResponse project = projectMapper.selectProjectById(id, userId);
        if (project == null) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("无权限访问该项目: projectId=%s", id);
        }
        return project;
    }

    @Override
    @Transactional
    public Long createProject(ProjectCreateRequest dto, Long userId) {
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
                throw new com.sunny.datapillar.common.exception.InternalException(e, "服务器内部错误");
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
    public void updateProject(Long id, ProjectUpdateRequest dto, Long userId) {
        Project existingProject = projectMapper.selectOne(
                new LambdaQueryWrapper<Project>()
                        .eq(Project::getId, id)
                        .eq(Project::getOwnerId, userId)
        );

        if (existingProject == null) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("无权限访问该项目: projectId=%s", id);
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
                throw new com.sunny.datapillar.common.exception.InternalException(e, "服务器内部错误");
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
            throw new com.sunny.datapillar.common.exception.ForbiddenException("无权限访问该项目: projectId=%s", id);
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
