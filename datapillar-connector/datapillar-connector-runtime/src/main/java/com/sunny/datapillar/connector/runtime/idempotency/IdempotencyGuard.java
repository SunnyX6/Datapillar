package com.sunny.datapillar.connector.runtime.idempotency;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sunny.datapillar.common.constant.ErrorType;
import com.sunny.datapillar.connector.spi.ConnectorResponse;
import com.sunny.datapillar.connector.spi.IdempotencyDescriptor;
import java.util.function.Supplier;

/** Runtime idempotency guard. */
public class IdempotencyGuard {

  private final ConnectorIdempotencyStore idempotencyStore;

  public IdempotencyGuard(ConnectorIdempotencyStore idempotencyStore) {
    this.idempotencyStore = idempotencyStore;
  }

  public ConnectorResponse execute(
      IdempotencyDescriptor descriptor, Supplier<ConnectorResponse> action) {
    if (descriptor == null || !descriptor.enabled()) {
      return action.get();
    }

    OperationState state = idempotencyStore.startOrResume(descriptor.key(), descriptor.step());
    if (state.status() == OperationStatus.SUCCEEDED) {
      return ConnectorResponse.of(JsonNodeFactory.instance.objectNode().put("idempotent", true));
    }

    try {
      ConnectorResponse response = action.get();
      idempotencyStore.markSucceeded(descriptor.key(), descriptor.step());
      return response;
    } catch (RuntimeException ex) {
      idempotencyStore.markFailed(
          descriptor.key(), descriptor.step(), ErrorType.INTERNAL_ERROR, ex.getMessage());
      throw ex;
    }
  }
}
