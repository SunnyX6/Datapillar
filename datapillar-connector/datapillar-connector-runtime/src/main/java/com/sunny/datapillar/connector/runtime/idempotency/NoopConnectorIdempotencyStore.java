package com.sunny.datapillar.connector.runtime.idempotency;

/** No-op idempotency store used when persistence is not configured. */
public class NoopConnectorIdempotencyStore implements ConnectorIdempotencyStore {

  @Override
  public OperationState startOrResume(String key, String step) {
    return new OperationState(key, step, OperationStatus.PROCESSING, null, null);
  }

  @Override
  public void markSucceeded(String key, String step) {}

  @Override
  public void markFailed(String key, String step, String errorType, String errorMessage) {}
}
