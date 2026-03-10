package com.sunny.datapillar.openlineage.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Response for DLQ replay operation. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DlqReplayResponse {

  private String status;
  private Long tenantId;
  private String sourceTopic;
  private String replayTopic;
  private String messageId;

  public String status() {
    return status;
  }

  public Long tenantId() {
    return tenantId;
  }

  public String sourceTopic() {
    return sourceTopic;
  }

  public String replayTopic() {
    return replayTopic;
  }

  public String messageId() {
    return messageId;
  }
}
