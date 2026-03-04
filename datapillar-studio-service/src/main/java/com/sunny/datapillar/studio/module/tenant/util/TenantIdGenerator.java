package com.sunny.datapillar.studio.module.tenant.util;

import com.sunny.datapillar.common.exception.InternalException;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * tenant snowflakeIDbuzzer Responsible for generating globally unique tenantsID
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class TenantIdGenerator {

  private static final long CUSTOM_EPOCH_MILLIS = 1735689600000L;
  private static final long WORKER_ID_BITS = 10L;
  private static final long SEQUENCE_BITS = 12L;
  private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
  private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
  private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
  private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

  private final long workerId;
  private final LongSupplier clock;

  private long lastTimestamp = -1L;
  private long sequence = 0L;

  @Autowired
  public TenantIdGenerator(@Value("${tenant.id-generator.worker-id:1}") long workerId) {
    this(workerId, System::currentTimeMillis);
  }

  TenantIdGenerator(long workerId, LongSupplier clock) {
    if (workerId < 0 || workerId > MAX_WORKER_ID) {
      throw new InternalException("tenant workerId out of range: %s", workerId);
    }
    if (clock == null) {
      throw new InternalException("tenant The issuing clock cannot be empty");
    }
    this.workerId = workerId;
    this.clock = clock;
  }

  public synchronized long nextId() {
    long currentTimestamp = clock.getAsLong();
    if (currentTimestamp < lastTimestamp) {
      currentTimestamp = waitUntil(lastTimestamp);
      if (currentTimestamp < lastTimestamp) {
        throw new InternalException(
            "tenant The clock is dialed back when issuing numbers.，Refuse to call: last=%s,current=%s",
            lastTimestamp, currentTimestamp);
      }
    }

    if (currentTimestamp == lastTimestamp) {
      sequence = (sequence + 1) & SEQUENCE_MASK;
      if (sequence == 0) {
        currentTimestamp = waitUntil(lastTimestamp + 1);
      }
    } else {
      sequence = 0;
    }

    lastTimestamp = currentTimestamp;
    return ((currentTimestamp - CUSTOM_EPOCH_MILLIS) << TIMESTAMP_SHIFT)
        | (workerId << WORKER_ID_SHIFT)
        | sequence;
  }

  private long waitUntil(long targetTimestamp) {
    long now = clock.getAsLong();
    while (now < targetTimestamp) {
      now = clock.getAsLong();
    }
    return now;
  }
}
