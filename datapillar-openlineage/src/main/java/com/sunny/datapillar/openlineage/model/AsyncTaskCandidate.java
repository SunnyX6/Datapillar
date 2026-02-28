package com.sunny.datapillar.openlineage.model;

import lombok.Builder;

/**
 * 异步任务候选。
 */
@Builder
public record AsyncTaskCandidate(
        AsyncTaskType taskType,
        String resourceType,
        String resourceId,
        String contentHash,
        String modelFingerprint,
        String payload
) {
}
