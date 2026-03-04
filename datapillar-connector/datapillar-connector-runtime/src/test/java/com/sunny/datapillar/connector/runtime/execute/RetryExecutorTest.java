package com.sunny.datapillar.connector.runtime.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RetryExecutorTest {

  private final RetryExecutor retryExecutor = new RetryExecutor();

  @Test
  void execute_shouldRetryUntilSuccess() {
    AtomicInteger attempts = new AtomicInteger(0);

    String value =
        retryExecutor.execute(
            3,
            Duration.ZERO,
            () -> {
              int current = attempts.incrementAndGet();
              if (current < 3) {
                throw new RuntimeException("temporary");
              }
              return "ok";
            });

    assertEquals("ok", value);
    assertEquals(3, attempts.get());
  }

  @Test
  void execute_shouldThrowWhenExhausted() {
    AtomicInteger attempts = new AtomicInteger(0);

    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                retryExecutor.execute(
                    2,
                    Duration.ZERO,
                    () -> {
                      attempts.incrementAndGet();
                      throw new RuntimeException("always-fail");
                    }));

    assertEquals("always-fail", exception.getMessage());
    assertEquals(2, attempts.get());
  }
}
