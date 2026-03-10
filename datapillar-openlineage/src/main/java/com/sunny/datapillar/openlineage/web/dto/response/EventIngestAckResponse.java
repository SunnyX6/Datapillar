package com.sunny.datapillar.openlineage.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Event ingestion acknowledgement payload. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventIngestAckResponse {

  private String status;
  private String eventType;
  private String runId;
  private Long tenantId;
  private String messageId;

  public String status() {
    return status;
  }

  public String eventType() {
    return eventType;
  }

  public String runId() {
    return runId;
  }

  public Long tenantId() {
    return tenantId;
  }

  public String messageId() {
    return messageId;
  }
}
