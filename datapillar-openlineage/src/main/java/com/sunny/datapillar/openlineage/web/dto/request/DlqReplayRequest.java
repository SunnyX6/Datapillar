package com.sunny.datapillar.openlineage.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Request for DLQ replay payload. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DlqReplayRequest {

  @NotBlank(message = "payload cannot be empty")
  private String payload;

  private Integer attempt;

  public String payload() {
    return payload;
  }

  public Integer attempt() {
    return attempt;
  }
}
