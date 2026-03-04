package com.sunny.datapillar.connector.runtime.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sunny.datapillar.common.exception.ServiceUnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TimeoutExecutorTest {

  private final TimeoutExecutor timeoutExecutor = new TimeoutExecutor();

  @AfterEach
  void tearDown() {
    timeoutExecutor.shutdown();
  }

  @Test
  void execute_shouldReturnWhenWithinTimeout() {
    String value = timeoutExecutor.execute(Duration.ofMillis(200), () -> "ok");

    assertEquals("ok", value);
  }

  @Test
  void execute_shouldThrowWhenTimeoutExceeded() {
    ServiceUnavailableException exception =
        assertThrows(
            ServiceUnavailableException.class,
            () ->
                timeoutExecutor.execute(
                    Duration.ofMillis(50),
                    () -> {
                      sleep(150);
                      return "slow";
                    }));

    assertEquals("Connector invocation timeout", exception.getMessage());
  }

  @Test
  void execute_shouldPropagateRuntimeException() {
    RuntimeException exception =
        assertThrows(
            RuntimeException.class,
            () ->
                timeoutExecutor.execute(
                    Duration.ofMillis(200),
                    () -> {
                      throw new RuntimeException("boom");
                    }));

    assertEquals("boom", exception.getMessage());
  }

  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(interruptedException);
    }
  }
}
