package com.sunny.datapillar.connector.spi;

/** Idempotency control metadata for write operations. */
public record IdempotencyDescriptor(String key, String step) {

  public static IdempotencyDescriptor of(String key, String step) {
    return new IdempotencyDescriptor(key, step);
  }

  public boolean enabled() {
    return key != null && !key.isBlank();
  }
}
