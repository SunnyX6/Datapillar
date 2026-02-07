package com.sunny.datapillar.studio.module.projects.service.impl;

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
import com.sunny.datapillar.studio.module.projects.dto.ProjectDto;
import com.sunny.datapillar.studio.module.projects.entity.Project;
import com.sunny.datapillar.studio.module.projects.enums.ProjectStatus;
import com.sunny.datapillar.studio.module.projects.mapper.ProjectMapper;
import com.sunny.datapillar.studio.module.projects.service.ProjectService;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 项目服务实现类
 *
 * @author sunny
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private final ProjectMapper projectMapper;
    private final ObjectMapper objectMapper;

    @Override
    public IPage<ProjectDto.Response> getProjectPage(ProjectDto.Query query, Long userId) {
        Page<ProjectDto.Response> page = new Page<>(query.getPage(), query.getSize());
        return projectMapper.selectProjectPage(page, query, userId);
    }

    @Override
    public IPage<ProjectDto.Response> getMyProjects(ProjectDto.Query query, Long userId, boolean isAdmin) {
        Page<ProjectDto.Response> page = new Page<>(query.getPage(), query.getSize());
        return projectMapper.selectMyProjects(page, query, userId, isAdmin);
    }

    @Override
    public ProjectDto.Response getProjectById(Long id, Long userId) {
        ProjectDto.Response project = projectMapper.selectProjectById(id, userId);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, id);
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
                project.setTags("[]");
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
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, id);
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
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, id);
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
