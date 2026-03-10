package com.sunny.datapillar.openlineage.pipeline;

import com.sunny.datapillar.openlineage.config.OpenLineageRuntimeConfig;
import org.springframework.stereotype.Component;

/** Retry policy for OpenLineage MQ consumers. */
@Component
public class MqRetryPolicy {

  private final OpenLineageRuntimeConfig properties;

  public MqRetryPolicy(OpenLineageRuntimeConfig properties) {
    this.properties = properties;
  }

  public boolean shouldRetry(int attempt) {
    return attempt < properties.getMq().getRetry().getMaxAttempts();
  }

  public int nextDelaySeconds(int attempt) {
    int first = Math.max(1, properties.getMq().getRetry().getFirstDelaySeconds());
    int max = Math.max(first, properties.getMq().getRetry().getMaxDelaySeconds());
    long delay = first * (1L << Math.max(0, attempt));
    return (int) Math.min(delay, max);
  }
}
