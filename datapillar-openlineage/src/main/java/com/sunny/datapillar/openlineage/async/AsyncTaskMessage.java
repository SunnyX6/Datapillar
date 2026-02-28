package com.sunny.datapillar.openlineage.async;

/**
 * Worker 推送任务。
 */
public record AsyncTaskMessage(
        long taskId,
        String claimToken
) {
}
