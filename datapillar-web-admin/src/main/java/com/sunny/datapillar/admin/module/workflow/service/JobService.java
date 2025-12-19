package com.sunny.datapillar.admin.module.workflow.service;

import java.util.List;

import com.sunny.datapillar.admin.module.workflow.dto.JobDto;

/**
 * 任务服务接口
 *
 * @author sunny
 */
public interface JobService {

    /**
     * 查询工作流下的所有任务
     */
    List<JobDto.Response> getJobsByWorkflowId(Long workflowId);

    /**
     * 获取任务详情
     */
    JobDto.Response getJobDetail(Long id);

    /**
     * 创建任务
     */
    Long createJob(Long workflowId, JobDto.Create dto);

    /**
     * 更新任务
     */
    void updateJob(Long id, JobDto.Update dto);

    /**
     * 删除任务
     */
    void deleteJob(Long id);

    /**
     * 批量更新任务位置
     */
    void updateJobPositions(Long workflowId, JobDto.LayoutSave dto);
}
