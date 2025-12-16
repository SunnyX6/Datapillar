package com.sunny.job.worker.pekko.actor;

import com.sunny.job.core.enums.JobStatus;
import com.sunny.job.core.message.ExecutorMessage.ExecuteJob;
import com.sunny.job.core.message.JobRunInfo;

/**
 * JobExecutor 上下文接口
 * <p>
 * 解耦 Executor 与 DB 操作、任务执行逻辑
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public interface JobExecutorContext {

    /**
     * 同步执行任务（由 Pekko Virtual Thread Dispatcher 管理线程）
     *
     * @param job 任务信息
     * @return 执行结果
     */
    ExecutionResult execute(ExecuteJob job);

    // ==================== 容量检查相关 ====================

    /**
     * 检查是否有可用容量
     *
     * @return true 如果有可用容量
     */
    boolean hasCapacity();

    /**
     * 获取当前运行中的任务数
     *
     * @return 运行中的任务数
     */
    int getRunningTaskCount();

    /**
     * 获取最大并发任务数
     *
     * @return 最大并发任务数
     */
    int getMaxConcurrentTasks();

    /**
     * 获取可用容量
     *
     * @return 可用容量
     */
    int getAvailableCapacity();

    /**
     * 取消执行中的任务
     *
     * @param jobRunId 任务执行实例 ID
     */
    void cancelExecution(long jobRunId);

    /**
     * 更新任务状态
     *
     * @param jobRunId   任务执行实例 ID
     * @param status     新状态
     * @param splitStart 分片起点（-1 表示非分片任务）
     */
    void updateJobRunStatus(long jobRunId, JobStatus status, long splitStart);

    /**
     * 更新 workflow_run 的 nextTriggerTime（任务开始执行时调用）
     * <p>
     * 在 workflow_run 的第一个任务开始执行时，计算并更新 nextTriggerTime
     * 通过 CAS 确保只更新一次（workflow_run.status 从 WAITING 变为 RUNNING）
     *
     * @param workflowRunId 工作流执行实例 ID
     */
    void updateNextTriggerTimeIfNeeded(long workflowRunId);

    /**
     * 更新任务状态（重试）
     *
     * @param jobRunId   任务执行实例 ID
     * @param retryCount 新的重试次数
     */
    void updateJobRunForRetry(long jobRunId, int retryCount);

    /**
     * 更新任务最终状态（分片任务汇聚后）
     *
     * @param jobRunId 任务执行实例 ID
     * @param status   最终状态
     */
    void updateJobRunFinalStatus(long jobRunId, JobStatus status);

    // ==================== 生成下一个 job_run 相关 ====================

    /**
     * 检查 workflow_run 是否已完成
     * <p>
     * 规则：所有 job_run 都不是 WAITING/RUNNING 状态
     *
     * @param workflowRunId 工作流执行实例 ID
     * @return 是否已完成
     */
    boolean checkWorkflowRunCompleted(long workflowRunId);

    /**
     * 计算 workflow_run 最终状态
     * <p>
     * 规则：全部 SUCCESS 才成功，否则失败
     *
     * @param workflowRunId 工作流执行实例 ID
     * @return 最终状态
     */
    JobStatus calculateWorkflowRunFinalStatus(long workflowRunId);

    /**
     * 更新 workflow_run 状态
     *
     * @param workflowRunId 工作流执行实例 ID
     * @param status        新状态
     * @param message       结果消息
     */
    void updateWorkflowRunStatus(long workflowRunId, JobStatus status, String message);

    /**
     * 生成下一个 workflow_run 及其 job_run
     * <p>
     * 条件：workflow 仍然上线且当前执行成功
     * 动作：计算下次触发时间，创建新的 workflow_run + job_run + job_run_dependency
     *
     * @param workflowRunId 当前工作流执行实例 ID
     * @return 新生成的 job_run ID 列表（用于通知 Dispatcher），为空表示不生成
     */
    GenerateNextResult generateNextWorkflowRun(long workflowRunId);

    // ==================== 告警相关 ====================

    /**
     * 触发告警
     * <p>
     * 根据告警规则发送告警通知
     *
     * @param jobId         任务 ID
     * @param jobRunId      任务执行实例 ID
     * @param workflowRunId 工作流执行实例 ID
     * @param namespaceId   命名空间 ID
     * @param jobName       任务名称
     * @param status        任务状态
     * @param message       执行结果消息
     */
    void triggerAlert(long jobId, long jobRunId, long workflowRunId,
                      long namespaceId, String jobName, JobStatus status, String message);

    /**
     * 生成下一个 workflow_run 的结果
     */
    record GenerateNextResult(
            boolean generated,
            long nextWorkflowRunId,
            long nextTriggerTime,
            java.util.List<JobRunInfo> jobRunInfoList
    ) {
        public static GenerateNextResult empty() {
            return new GenerateNextResult(false, 0, 0, java.util.List.of());
        }
    }

    /**
     * 任务执行结果
     */
    record ExecutionResult(JobStatus status, String message) {
        public static ExecutionResult success(String message) {
            return new ExecutionResult(JobStatus.SUCCESS, message != null ? message : "执行成功");
        }

        public static ExecutionResult failure(String message) {
            return new ExecutionResult(JobStatus.FAIL, message);
        }

        public static ExecutionResult timeout(String message) {
            return new ExecutionResult(JobStatus.TIMEOUT, message);
        }

        public boolean isSuccess() {
            return status == JobStatus.SUCCESS;
        }

        public boolean isTimeout() {
            return status == JobStatus.TIMEOUT;
        }
    }
}
