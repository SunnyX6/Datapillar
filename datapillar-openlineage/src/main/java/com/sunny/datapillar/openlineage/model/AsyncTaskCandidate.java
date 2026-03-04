package com.sunny.datapillar.openlineage.model;

import lombok.Builder;

/** Asynchronous task candidates. */
@Builder
public record AsyncTaskCandidate(
    AsyncTaskType taskType,
    String resourceType,
    String resourceId,
    String contentHash,
    String modelFingerprint,
    String payload) {}
