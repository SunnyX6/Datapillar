package com.sunny.datapillar.studio.module.workflow.service;

import java.util.List;

import com.sunny.datapillar.studio.module.workflow.dto.JobDependencyDto;

/**
 * 任务Dependency服务
 * 提供任务Dependency业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface JobDependencyService {

    /**
     * 查询工作流下的所有依赖
     */
    List<JobDependencyDto.Response> getDependenciesByWorkflowId(Long workflowId);

    /**
     * 查询任务的上游依赖
     */
    List<JobDependencyDto.Response> getDependenciesByJobId(Long jobId);

    /**
     * 创建依赖关系
     */
    Long createDependency(Long workflowId, JobDependencyDto.Create dto);

    /**
     * 删除依赖关系
     */
    void deleteDependency(Long jobId, Long parentJobId);
}
