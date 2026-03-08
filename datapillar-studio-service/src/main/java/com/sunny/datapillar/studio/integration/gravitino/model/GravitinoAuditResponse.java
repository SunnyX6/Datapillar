package com.sunny.datapillar.studio.integration.gravitino.model;

import java.time.Instant;
import lombok.Data;

@Data
public class GravitinoAuditResponse {

  private String creator;

  private Instant createTime;

  private String lastModifier;

  private Instant lastModifiedTime;
}
