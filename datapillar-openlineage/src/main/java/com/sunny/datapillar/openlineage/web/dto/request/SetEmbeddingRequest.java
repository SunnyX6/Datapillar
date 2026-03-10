package com.sunny.datapillar.openlineage.web.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request for setting DW embedding binding. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SetEmbeddingRequest {

  @NotNull(message = "aiModelId cannot be empty")
  private Long aiModelId;

  public Long aiModelId() {
    return aiModelId;
  }
}
