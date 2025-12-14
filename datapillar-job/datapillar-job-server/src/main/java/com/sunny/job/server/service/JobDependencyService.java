package com.sunny.job.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunny.job.server.entity.JobDependency;

import java.util.List;

/**
 * 任务依赖 Service
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public interface JobDependencyService extends IService<JobDependency> {

    /**
     * 验证依赖关系是否存在环
     * <p>
     * 使用 DagGraph 进行环检测，如果存在环则抛出异常
     *
     * @param workflowId   工作流 ID
     * @param dependencies 依赖关系列表
     * @throws IllegalArgumentException 如果存在循环依赖
     */
    void validateNoCycle(Long workflowId, List<JobDependency> dependencies);

    /**
     * 验证新增单条依赖是否会产生环
     *
     * @param dependency 新增的依赖
     * @throws IllegalArgumentException 如果会产生循环依赖
     */
    void validateSingleDependency(JobDependency dependency);
}
