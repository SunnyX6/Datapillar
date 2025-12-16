package com.sunny.job.worker.domain.mapper;

import com.sunny.job.worker.domain.entity.JobWorkflowRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
     * 根据 ID 查询工作流执行实例
     *
     * @param id 工作流执行实例ID
     * @return 工作流执行实例
     */
    JobWorkflowRun selectById(@Param("id") Long id);

    /**
     * 更新工作流执行实例状态
     *
     * @param workflowRunId 工作流执行实例ID
     * @param status        新状态
     * @param op            操作类型
     * @param endTime       结束时间（毫秒）
     * @param message       结果消息
     * @return 影响行数
     */
    int updateStatus(@Param("workflowRunId") Long workflowRunId,
                     @Param("status") Integer status,
                     @Param("op") String op,
                     @Param("endTime") Long endTime,
                     @Param("message") String message);

    /**
     * CAS 更新状态和 nextTriggerTime
     * <p>
     * 只有当前状态等于 expectedStatus 时才更新
     *
     * @param workflowRunId   工作流执行实例ID
     * @param expectedStatus  期望的当前状态
     * @param newStatus       新状态
     * @param startTime       开始时间（毫秒）
     * @param nextTriggerTime 下一次触发时间（毫秒）
     * @return 影响行数（0 表示 CAS 失败）
     */
    int updateStatusAndNextTriggerTime(@Param("workflowRunId") Long workflowRunId,
                                        @Param("expectedStatus") Integer expectedStatus,
                                        @Param("newStatus") Integer newStatus,
                                        @Param("startTime") Long startTime,
                                        @Param("nextTriggerTime") Long nextTriggerTime);

    /**
     * 查询工作流执行实例的工作流ID
     *
     * @param workflowRunId 工作流执行实例ID
     * @return 工作流ID
     */
    Long selectWorkflowIdByRunId(@Param("workflowRunId") Long workflowRunId);

    /**
     * 查询状态为 RUNNING 且 nextTriggerTime 有值的 workflow_run
     * <p>
     * 用于服务重启时恢复未完成的调度
     *
     * @return 需要检查恢复的 workflow_run 列表
     */
    List<JobWorkflowRun> selectRunningWithNextTriggerTime();

    /**
     * 检查是否已存在指定 workflowId 和 triggerTime 的 workflow_run
     *
     * @param workflowId  工作流ID
     * @param triggerTime 触发时间
     * @return 是否存在
     */
    boolean existsByWorkflowIdAndTriggerTime(@Param("workflowId") Long workflowId, @Param("triggerTime") Long triggerTime);
}
