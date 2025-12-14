package com.sunny.job.worker.domain.mapper;

import com.sunny.job.worker.domain.entity.JobWorkflowRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 工作流执行实例 Mapper
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
@Mapper
public interface JobWorkflowRunMapper {

    /**
     * 插入工作流执行实例
     *
     * @param workflowRun 工作流执行实例
     * @return 影响行数
     */
    int insert(JobWorkflowRun workflowRun);

    /**
     * 更新工作流执行实例状态
     *
     * @param workflowRunId 工作流执行实例ID
     * @param status        新状态
     * @param endTime       结束时间（毫秒）
     * @param message       结果消息
     * @return 影响行数
     */
    int updateStatus(@Param("workflowRunId") Long workflowRunId,
                     @Param("status") Integer status,
                     @Param("endTime") Long endTime,
                     @Param("message") String message);

    /**
     * 查询工作流执行实例的工作流ID
     *
     * @param workflowRunId 工作流执行实例ID
     * @return 工作流ID
     */
    Long selectWorkflowIdByRunId(@Param("workflowRunId") Long workflowRunId);
}
