package com.sunny.datapillar.openlineage.source.event;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** MQ body for OpenLineage dead-letter topics. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterEvent {

  private String sourceTopic;
  private String consumerGroup;
  private String reason;
  private int attempt;
  private long failedAt;
  private Map<String, String> originalHeaders = new LinkedHashMap<>();
  private String originalBody;

  public String sourceTopic() {
    return sourceTopic;
  }

  public String consumerGroup() {
    return consumerGroup;
  }

  public String reason() {
    return reason;
  }

  public int attempt() {
    return attempt;
  }

  public long failedAt() {
    return failedAt;
  }

  public Map<String, String> originalHeaders() {
    return originalHeaders;
  }

  public String originalBody() {
    return originalBody;
  }
}
