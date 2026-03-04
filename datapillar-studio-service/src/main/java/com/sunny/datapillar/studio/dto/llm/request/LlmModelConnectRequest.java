package com.sunny.datapillar.studio.dto.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "LlmModelConnectRequest")
public class LlmModelConnectRequest {

  @NotBlank(message = "api_key cannot be empty")
  private String apiKey;

  @Size(max = 255, message = "base_url The length cannot exceed 255")
  private String baseUrl;
}
