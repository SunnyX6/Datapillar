package com.sunny.datapillar.openlineage.async;

/** Worker push task. */
public record AsyncTaskMessage(long taskId, String claimToken) {}
