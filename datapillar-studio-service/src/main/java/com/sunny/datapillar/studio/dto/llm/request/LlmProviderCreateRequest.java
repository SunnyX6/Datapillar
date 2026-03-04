package com.sunny.datapillar.studio.dto.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "LlmProviderCreateRequest")
public class LlmProviderCreateRequest {

  @NotBlank(message = "code cannot be empty")
  @Size(max = 32, message = "code The length cannot exceed 32")
  private String code;

  @Size(max = 64, message = "name The length cannot exceed 64")
  private String name;

  @Size(max = 255, message = "base_url The length cannot exceed 255")
  private String baseUrl;
}
