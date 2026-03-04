package com.sunny.datapillar.connector.spi;

import java.time.Duration;

/** Timeout and retry descriptor used by runtime. */
public record TimeoutDescriptor(Duration timeout, Integer maxAttempts, Duration backoff) {

  public static TimeoutDescriptor of(Duration timeout, Integer maxAttempts, Duration backoff) {
    return new TimeoutDescriptor(timeout, maxAttempts, backoff);
  }
}
