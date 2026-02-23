package com.sunny.datapillar.studio.module.workflow.service;

import java.util.List;

import com.sunny.datapillar.studio.module.workflow.dto.JobDto;

/**
 * 任务服务
 * 提供任务业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface JobService {

    /**
     * 查询工作流下的所有任务
     */
    List<JobDto.Response> getJobsByWorkflowId(Long workflowId);

    /**
     * 获取任务详情
     */
    JobDto.Response getJobDetail(Long workflowId, Long id);

    /**
     * 创建任务
     */
    Long createJob(Long workflowId, JobDto.Create dto);

    /**
     * 更新任务
     */
    void updateJob(Long workflowId, Long id, JobDto.Update dto);

    /**
     * 删除任务
     */
    void deleteJob(Long workflowId, Long id);

    /**
     * 批量更新任务位置
     */
    void updateJobPositions(Long workflowId, JobDto.LayoutSave dto);
}
