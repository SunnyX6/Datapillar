package com.sunny.datapillar.studio.dto.semantic.response;

import java.time.Instant;
import lombok.Data;

@Data
public class AuditResponse {

  private String creator;

  private Instant createTime;

  private String lastModifier;

  private Instant lastModifiedTime;
}
