package com.sunny.datapillar.studio.module.tenant.util;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TenantIdGeneratorTest {

  private static final long BASE_TIMESTAMP = 1735689601000L;

  @Test
  void nextId_shouldGenerateUniqueIds() {
    TenantIdGenerator generator = new TenantIdGenerator(1L, System::currentTimeMillis);
    Set<Long> ids = new HashSet<>();

    for (int i = 0; i < 20000; i++) {
      ids.add(generator.nextId());
    }

    Assertions.assertEquals(20000, ids.size());
  }

  @Test
  void nextId_shouldWaitWhenClockMovesBackward() {
    AtomicInteger index = new AtomicInteger(0);
    long[] timeline =
        new long[] {
          BASE_TIMESTAMP,
          BASE_TIMESTAMP + 1,
          BASE_TIMESTAMP - 1,
          BASE_TIMESTAMP + 1,
          BASE_TIMESTAMP + 2
        };
    LongSupplier clock =
        () -> {
          int i = index.getAndUpdate(v -> Math.min(v + 1, timeline.length - 1));
          return timeline[i];
        };
    TenantIdGenerator generator = new TenantIdGenerator(2L, clock);

    long first = generator.nextId();
    long second = generator.nextId();
    long third = generator.nextId();

    Assertions.assertTrue(second > first);
    Assertions.assertTrue(third > second);
  }

  @Test
  void nextId_shouldWaitForNextMillisWhenSequenceExhausted() {
    AtomicInteger calls = new AtomicInteger(0);
    LongSupplier clock =
        () -> calls.incrementAndGet() <= 5000 ? BASE_TIMESTAMP : BASE_TIMESTAMP + 1;
    TenantIdGenerator generator = new TenantIdGenerator(3L, clock);

    long previous = -1L;
    for (int i = 0; i < 4097; i++) {
      long current = generator.nextId();
      Assertions.assertTrue(current > previous);
      previous = current;
    }
  }
}
