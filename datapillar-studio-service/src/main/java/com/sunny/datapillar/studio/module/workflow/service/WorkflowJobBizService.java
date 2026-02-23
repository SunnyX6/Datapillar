package com.sunny.datapillar.studio.module.workflow.service;

import com.sunny.datapillar.studio.module.workflow.dto.JobDto;
import java.util.List;

/**
 * 工作流任务业务服务
 * 提供工作流任务业务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface WorkflowJobBizService {

    List<JobDto.Response> getJobsByWorkflowId(Long workflowId);

    JobDto.Response getJobDetail(Long workflowId, Long id);

    Long createJob(Long workflowId, JobDto.Create dto);

    void updateJob(Long workflowId, Long id, JobDto.Update dto);

    void deleteJob(Long workflowId, Long id);

    void updateJobPositions(Long workflowId, JobDto.LayoutSave dto);
}
