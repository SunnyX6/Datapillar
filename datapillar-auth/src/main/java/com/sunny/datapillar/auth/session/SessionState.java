package com.sunny.datapillar.auth.session;

import lombok.Builder;
import lombok.Data;

/** Session state aggregate. */
@Data
@Builder
public class SessionState {

  private String sid;
  private Long tenantId;
  private Long userId;
  private String accessJti;
  private String refreshJti;
}
