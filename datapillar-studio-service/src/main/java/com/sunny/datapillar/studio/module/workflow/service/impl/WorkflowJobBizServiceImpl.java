package com.sunny.datapillar.studio.module.workflow.service.impl;

import com.sunny.datapillar.studio.module.workflow.dto.JobDto;
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
    public List<JobDto.Response> getJobsByWorkflowId(Long workflowId) {
        return jobService.getJobsByWorkflowId(workflowId);
    }

    @Override
    public JobDto.Response getJobDetail(Long id) {
        return jobService.getJobDetail(id);
    }

    @Override
    public Long createJob(Long workflowId, JobDto.Create dto) {
        return jobService.createJob(workflowId, dto);
    }

    @Override
    public void updateJob(Long id, JobDto.Update dto) {
        jobService.updateJob(id, dto);
    }

    @Override
    public void deleteJob(Long id) {
        jobService.deleteJob(id);
    }

    @Override
    public void updateJobPositions(Long workflowId, JobDto.LayoutSave dto) {
        jobService.updateJobPositions(workflowId, dto);
    }
}
