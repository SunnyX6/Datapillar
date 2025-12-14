package com.sunny.job.core.strategy.route;

import java.util.List;

/**
 * 路由上下文
 * <p>
 * 包含路由决策所需的所有信息
 *
 * @author SunnyX6
 * @date 2025-12-13
 */
public record RouteContext(
        long jobId,
        long jobRunId,
        String jobParams,
        List<WorkerInfo> workers
) {

    /**
     * 创建路由上下文
     *
     * @param jobId     任务ID
     * @param jobRunId  任务实例ID
     * @param jobParams 任务参数（用于一致性哈希）
     * @param workers   可用 Worker 列表
     */
    public static RouteContext of(long jobId, long jobRunId, String jobParams, List<WorkerInfo> workers) {
        return new RouteContext(jobId, jobRunId, jobParams, workers);
    }

    /**
     * 创建简单路由上下文
     *
     * @param jobId   任务ID
     * @param workers 可用 Worker 列表
     */
    public static RouteContext of(long jobId, List<WorkerInfo> workers) {
        return new RouteContext(jobId, 0L, null, workers);
    }

    /**
     * 是否有可用 Worker
     */
    public boolean hasWorkers() {
        return workers != null && !workers.isEmpty();
    }

    /**
     * 获取可用 Worker 数量
     */
    public int workerCount() {
        return workers == null ? 0 : workers.size();
    }

    /**
     * 获取哈希键（用于一致性哈希）
     * <p>
     * 优先使用 jobParams，否则使用 jobId
     */
    public String hashKey() {
        if (jobParams != null && !jobParams.isEmpty()) {
            return jobParams;
        }
        return String.valueOf(jobId);
    }
}
