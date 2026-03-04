package com.sunny.datapillar.connector.runtime.idempotency;

/** Status for connector idempotency records. */
public enum OperationStatus {
  PROCESSING,
  SUCCEEDED,
  FAILED
}
