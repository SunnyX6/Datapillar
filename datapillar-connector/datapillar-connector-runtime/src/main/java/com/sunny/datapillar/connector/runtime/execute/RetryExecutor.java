package com.sunny.datapillar.connector.runtime.execute;

import java.time.Duration;
import java.util.function.Supplier;

/** Retry executor used by kernel. */
public class RetryExecutor {

  public <T> T execute(Integer maxAttempts, Duration backoff, Supplier<T> action) {
    int attempts = (maxAttempts == null || maxAttempts < 1) ? 1 : maxAttempts;
    Duration safeBackoff = backoff == null ? Duration.ZERO : backoff;

    RuntimeException last = null;
    for (int index = 1; index <= attempts; index++) {
      try {
        return action.get();
      } catch (RuntimeException ex) {
        last = ex;
        if (index >= attempts) {
          break;
        }
        sleepQuietly(safeBackoff);
      }
    }
    throw last;
  }

  private void sleepQuietly(Duration duration) {
    if (duration.isZero() || duration.isNegative()) {
      return;
    }
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
    }
  }
}
