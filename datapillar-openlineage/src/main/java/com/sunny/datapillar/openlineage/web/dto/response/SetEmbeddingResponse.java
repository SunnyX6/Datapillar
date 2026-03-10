package com.sunny.datapillar.openlineage.web.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Response of setEmbedding. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SetEmbeddingResponse {

  private Long tenantId;
  private String scope;
  private Long aiModelId;
  private Long revision;
  private Long setBy;
  private LocalDateTime setAt;

  public Long tenantId() {
    return tenantId;
  }

  public String scope() {
    return scope;
  }

  public Long aiModelId() {
    return aiModelId;
  }

  public Long revision() {
    return revision;
  }

  public Long setBy() {
    return setBy;
  }

  public LocalDateTime setAt() {
    return setAt;
  }
}
