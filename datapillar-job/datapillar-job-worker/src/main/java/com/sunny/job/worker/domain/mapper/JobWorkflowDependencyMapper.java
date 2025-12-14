package com.sunny.job.worker.domain.mapper;

import com.sunny.job.worker.domain.entity.JobWorkflowDependency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 跨工作流依赖 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobWorkflowDependencyMapper {

    /**
     * 查询工作流的跨工作流依赖
     *
     * @param workflowId 工作流ID
     * @return 跨工作流依赖列表
     */
    List<JobWorkflowDependency> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 查询依赖工作流的最新成功 workflow_run
     *
     * @param workflowId 工作流ID
     * @return 最新成功的 workflow_run ID，不存在返回 null
     */
    Long selectLatestSuccessWorkflowRunId(@Param("workflowId") Long workflowId);

    /**
     * 根据 workflow_run_id 和 job_id 查询对应的 job_run_id
     *
     * @param workflowRunId 工作流执行实例ID
     * @param jobId         任务ID
     * @return job_run_id，不存在返回 null
     */
    Long selectJobRunId(@Param("workflowRunId") Long workflowRunId,
                        @Param("jobId") Long jobId);
}
