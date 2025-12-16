package com.sunny.job.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunny.job.server.dto.Dependency;
import com.sunny.job.server.dto.Job;
import com.sunny.job.server.entity.JobWorkflow;

import java.util.List;
import java.util.Map;

/**
 * 工作流定义 Service
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public interface JobWorkflowService extends IService<JobWorkflow> {

    // ==================== 工作流操作 ====================

    /**
     * 创建工作流
     */
    Long createWorkflow(Long namespaceId, String workflowName, Integer triggerType,
                        String triggerValue, String description);

    /**
     * 更新工作流基本信息
     */
    void updateWorkflow(Long workflowId, String workflowName, Integer triggerType,
                        String triggerValue, Integer timeoutSeconds, Integer maxRetryTimes,
                        Integer priority, String description);

    /**
     * 删除工作流
     */
    void deleteWorkflow(Long workflowId);

    // ==================== 任务节点操作 ====================

    /**
     * 查询工作流下所有任务
     */
    List<Job> getJobs(Long workflowId);

    /**
     * 添加任务
     */
    Long addJob(Long workflowId, Job job);

    /**
     * 更新任务
     */
    void updateJob(Long workflowId, Long jobId, Job job);

    /**
     * 删除任务
     */
    void deleteJob(Long workflowId, Long jobId);

    // ==================== 依赖关系操作 ====================

    /**
     * 查询工作流下所有依赖
     */
    List<Dependency> getDependencies(Long workflowId);

    /**
     * 添加依赖
     */
    Long addDependency(Long workflowId, Long jobId, Long parentJobId);

    /**
     * 删除依赖
     */
    void deleteDependency(Long workflowId, Long jobId, Long parentJobId);

    // ==================== 布局操作 ====================

    /**
     * 批量保存节点位置
     */
    void saveLayout(Long workflowId, Map<Long, double[]> positions);

    // ==================== 工作流状态操作 ====================

    /**
     * 上线工作流
     */
    void online(Long workflowId);

    /**
     * 下线工作流
     */
    void offline(Long workflowId);

    /**
     * 手动触发工作流
     */
    void trigger(Long workflowId);
}
