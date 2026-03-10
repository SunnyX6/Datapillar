package com.sunny.datapillar.auth.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Internal request payload for API key context resolution. */
@Data
public class ApiKeyResolveRequest {

  @NotBlank(message = "apiKey must not be blank")
  private String apiKey;

  private String clientIp;

  private String traceId;
}
