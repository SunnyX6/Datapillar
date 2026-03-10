package com.sunny.datapillar.openlineage.web.dto.request;

import jakarta.validation.constraints.Null;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request for tenant full rebuild. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RebuildRequest {

  @Null(message = "/rebuild request must not contain aiModelId")
  private Long aiModelId;

  public Long aiModelId() {
    return aiModelId;
  }
}
