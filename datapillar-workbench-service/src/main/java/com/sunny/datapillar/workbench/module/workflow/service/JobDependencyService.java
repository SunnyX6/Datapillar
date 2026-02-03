package com.sunny.datapillar.workbench.module.workflow.service;

import java.util.List;

import com.sunny.datapillar.workbench.module.workflow.dto.JobDependencyDto;

/**
 * 任务依赖服务接口
 *
 * @author sunny
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
