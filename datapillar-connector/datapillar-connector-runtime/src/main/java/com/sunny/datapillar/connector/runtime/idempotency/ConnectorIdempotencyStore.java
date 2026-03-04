package com.sunny.datapillar.connector.runtime.idempotency;

/** Persistent idempotency storage for connector operations. */
public interface ConnectorIdempotencyStore {

  OperationState startOrResume(String key, String step);

  void markSucceeded(String key, String step);

  void markFailed(String key, String step, String errorType, String errorMessage);
}
