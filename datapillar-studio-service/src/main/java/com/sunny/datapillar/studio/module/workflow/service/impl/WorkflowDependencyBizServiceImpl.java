package com.sunny.datapillar.studio.module.workflow.service.impl;

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
import com.sunny.datapillar.studio.module.workflow.service.JobDependencyService;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowDependencyBizService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 工作流Dependency业务服务实现
 * 实现工作流Dependency业务业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class WorkflowDependencyBizServiceImpl implements WorkflowDependencyBizService {

    private final JobDependencyService jobDependencyService;

    @Override
    public List<JobDependencyResponse> getDependenciesByWorkflowId(Long workflowId) {
        return jobDependencyService.getDependenciesByWorkflowId(workflowId);
    }

    @Override
    public List<JobDependencyResponse> getDependenciesByJobId(Long jobId) {
        return jobDependencyService.getDependenciesByJobId(jobId);
    }

    @Override
    public Long createDependency(Long workflowId, JobDependencyCreateRequest dto) {
        return jobDependencyService.createDependency(workflowId, dto);
    }

    @Override
    public void deleteDependency(Long workflowId, Long jobId, Long parentJobId) {
        jobDependencyService.deleteDependency(workflowId, jobId, parentJobId);
    }
}
