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
import com.sunny.datapillar.studio.module.workflow.service.JobService;
import com.sunny.datapillar.studio.module.workflow.service.WorkflowJobBizService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 工作流任务业务服务实现
 * 实现工作流任务业务业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class WorkflowJobBizServiceImpl implements WorkflowJobBizService {

    private final JobService jobService;

    @Override
    public List<JobResponse> getJobsByWorkflowId(Long workflowId) {
        return jobService.getJobsByWorkflowId(workflowId);
    }

    @Override
    public JobResponse getJobDetail(Long workflowId, Long id) {
        return jobService.getJobDetail(workflowId, id);
    }

    @Override
    public Long createJob(Long workflowId, JobCreateRequest dto) {
        return jobService.createJob(workflowId, dto);
    }

    @Override
    public void updateJob(Long workflowId, Long id, JobUpdateRequest dto) {
        jobService.updateJob(workflowId, id, dto);
    }

    @Override
    public void deleteJob(Long workflowId, Long id) {
        jobService.deleteJob(workflowId, id);
    }

    @Override
    public void updateJobPositions(Long workflowId, JobLayoutSaveRequest dto) {
        jobService.updateJobPositions(workflowId, dto);
    }
}
