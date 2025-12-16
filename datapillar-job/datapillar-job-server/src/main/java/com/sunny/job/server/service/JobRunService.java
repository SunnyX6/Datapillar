package com.sunny.job.server.service;

/**
 * 任务运行实例 Service
 * <p>
 * 处理单个任务实例的操作：kill、pass、markFailed、retry、trigger
 * <p>
 * Server 只负责广播操作事件，由 Worker 执行具体逻辑
 *
 * @author SunnyX6
 * @date 2025-12-15
 */
public interface JobRunService {

    /**
     * 终止任务
     *
     * @param jobRunId 任务运行实例ID
     */
    void kill(Long jobRunId);

    /**
     * 跳过任务
     * <p>
     * 将任务标记为已跳过，视为成功，触发下游任务
     *
     * @param jobRunId 任务运行实例ID
     */
    void pass(Long jobRunId);

    /**
     * 标记任务失败
     *
     * @param jobRunId 任务运行实例ID
     */
    void markFailed(Long jobRunId);

    /**
     * 重试任务
     *
     * @param jobRunId      任务运行实例ID
     * @param jobId         任务ID
     * @param workflowRunId 工作流运行实例ID
     * @param namespaceId   命名空间ID
     * @param bucketId      桶ID
     */
    void retry(Long jobRunId, Long jobId, Long workflowRunId, Long namespaceId, Integer bucketId);

    /**
     * 手动触发任务
     *
     * @param jobRunId      任务运行实例ID
     * @param jobId         任务ID
     * @param workflowRunId 工作流运行实例ID
     * @param namespaceId   命名空间ID
     * @param bucketId      桶ID
     */
    void trigger(Long jobRunId, Long jobId, Long workflowRunId, Long namespaceId, Integer bucketId);
}
