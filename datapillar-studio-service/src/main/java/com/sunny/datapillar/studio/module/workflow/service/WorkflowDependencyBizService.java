package com.sunny.datapillar.studio.module.workflow.service;

import com.sunny.datapillar.studio.module.workflow.dto.JobDependencyDto;
import java.util.List;

/**
 * 工作流Dependency业务服务
 * 提供工作流Dependency业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface WorkflowDependencyBizService {

    List<JobDependencyDto.Response> getDependenciesByWorkflowId(Long workflowId);

    List<JobDependencyDto.Response> getDependenciesByJobId(Long jobId);

    Long createDependency(Long workflowId, JobDependencyDto.Create dto);

    void deleteDependency(Long workflowId, Long jobId, Long parentJobId);
}
