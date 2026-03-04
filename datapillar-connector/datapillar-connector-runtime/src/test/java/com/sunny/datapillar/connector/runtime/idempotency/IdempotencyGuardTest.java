package com.sunny.datapillar.connector.runtime.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sunny.datapillar.connector.spi.ConnectorResponse;
import com.sunny.datapillar.connector.spi.IdempotencyDescriptor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdempotencyGuardTest {

  @Test
  void execute_shouldBypassWhenDescriptorDisabled() {
    IdempotencyGuard guard = new IdempotencyGuard(new RecordingStore(OperationStatus.PROCESSING));
    AtomicInteger executed = new AtomicInteger(0);

    ConnectorResponse response =
        guard.execute(
            IdempotencyDescriptor.of("", "step"),
            () -> {
              executed.incrementAndGet();
              return ConnectorResponse.of(JsonNodeFactory.instance.objectNode().put("ok", true));
            });

    assertEquals(1, executed.get());
    assertEquals(true, response.payload().path("ok").asBoolean(false));
  }

  @Test
  void execute_shouldReturnIdempotentResponseWhenAlreadySucceeded() {
    RecordingStore store = new RecordingStore(OperationStatus.SUCCEEDED);
    IdempotencyGuard guard = new IdempotencyGuard(store);

    ConnectorResponse response =
        guard.execute(
            IdempotencyDescriptor.of("k1", "step"),
            () -> ConnectorResponse.of(JsonNodeFactory.instance.objectNode().put("ok", true)));

    assertEquals(true, response.payload().path("idempotent").asBoolean(false));
    assertEquals(0, store.markSucceededCount);
  }

  @Test
  void execute_shouldMarkSucceededAfterAction() {
    RecordingStore store = new RecordingStore(OperationStatus.PROCESSING);
    IdempotencyGuard guard = new IdempotencyGuard(store);

    guard.execute(
        IdempotencyDescriptor.of("k2", "step"),
        () -> ConnectorResponse.of(JsonNodeFactory.instance.objectNode().put("ok", true)));

    assertEquals(1, store.markSucceededCount);
  }

  @Test
  void execute_shouldMarkFailedWhenActionThrows() {
    RecordingStore store = new RecordingStore(OperationStatus.PROCESSING);
    IdempotencyGuard guard = new IdempotencyGuard(store);

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                guard.execute(
                    IdempotencyDescriptor.of("k3", "step"),
                    () -> {
                      throw new RuntimeException("boom");
                    }));

    assertEquals("boom", exception.getMessage());
    assertEquals(1, store.markFailedCount);
    assertEquals("INTERNAL_ERROR", store.lastErrorType);
  }

  private static final class RecordingStore implements ConnectorIdempotencyStore {
    private final OperationStatus initialStatus;
    private int markSucceededCount;
    private int markFailedCount;
    private String lastErrorType;

    private RecordingStore(OperationStatus initialStatus) {
      this.initialStatus = initialStatus;
    }

    @Override
    public OperationState startOrResume(String key, String step) {
      return new OperationState(key, step, initialStatus, null, null);
    }

    @Override
    public void markSucceeded(String key, String step) {
      markSucceededCount++;
    }

    @Override
    public void markFailed(String key, String step, String errorType, String errorMessage) {
      markFailedCount++;
      lastErrorType = errorType;
    }
  }
}
