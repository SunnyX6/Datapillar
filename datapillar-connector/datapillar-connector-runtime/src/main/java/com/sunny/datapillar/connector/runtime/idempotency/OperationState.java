package com.sunny.datapillar.connector.runtime.idempotency;

/** Connector operation idempotency state snapshot. */
public record OperationState(
    String key, String step, OperationStatus status, String errorType, String errorMessage) {}
