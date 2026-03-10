package com.sunny.datapillar.openlineage.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Response for rebuild trigger. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RebuildResponse {

  private String status;
  private Long tenantId;
  private Long aiModelId;
  private Long revision;
  private int graphUpserts;
  private int embeddingTasks;

  public String status() {
    return status;
  }

  public Long tenantId() {
    return tenantId;
  }

  public Long aiModelId() {
    return aiModelId;
  }

  public Long revision() {
    return revision;
  }

  public int graphUpserts() {
    return graphUpserts;
  }

  public int embeddingTasks() {
    return embeddingTasks;
  }
}
