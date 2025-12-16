package com.sunny.job.worker.domain.mapper;

import com.sunny.job.worker.domain.entity.JobDependency;
import com.sunny.job.worker.domain.entity.JobRunDependency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 依赖关系 Mapper
 * <p>
 * 处理 job_dependency（设计阶段）和 job_run_dependency（执行阶段）
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobDependencyMapper {

    /**
     * 查询任务的上游依赖（执行阶段）
     *
     * @param jobRunId 任务执行实例ID
     * @return 上游任务ID列表
     */
    List<Long> selectParentRunIds(@Param("jobRunId") Long jobRunId);

    /**
     * 批量查询多个任务的上游依赖（执行阶段）
     * <p>
     * 优化 N+1 查询问题，一次查询所有任务的依赖关系
     *
     * @param jobRunIds 任务执行实例ID列表
     * @return 依赖关系列表（job_run_id -> parent_run_id）
     */
    List<JobRunDependency> selectParentRunIdsBatch(@Param("jobRunIds") List<Long> jobRunIds);

    /**
     * 查询工作流下所有任务依赖关系（设计阶段）
     *
     * @param workflowId 工作流ID
     * @return 依赖关系列表
     */
    List<JobDependency> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 批量插入任务执行依赖关系
     *
     * @param dependencies 依赖关系列表
     * @return 影响行数
     */
    int batchInsertRunDependencies(@Param("list") List<JobRunDependency> dependencies);

    /**
     * 插入单条任务执行依赖关系
     *
     * @param workflowRunId 工作流执行实例ID
     * @param jobRunId      任务执行实例ID
     * @param parentRunId   父任务执行实例ID
     * @return 影响行数
     */
    int insertRunDependency(@Param("workflowRunId") Long workflowRunId,
                            @Param("jobRunId") Long jobRunId,
                            @Param("parentRunId") Long parentRunId);
}
