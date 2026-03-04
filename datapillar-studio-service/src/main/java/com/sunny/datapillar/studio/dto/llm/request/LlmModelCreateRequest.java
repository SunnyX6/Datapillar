package com.sunny.datapillar.studio.dto.llm.request;

import com.sunny.datapillar.studio.module.llm.enums.AiModelType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "LlmModelCreateRequest")
public class LlmModelCreateRequest {

  @NotBlank(message = "provider_model_id cannot be empty")
  @Size(max = 128, message = "provider_model_id The length cannot exceed 128")
  private String providerModelId;

  @NotBlank(message = "name cannot be empty")
  @Size(max = 128, message = "name The length cannot exceed 128")
  private String name;

  @NotBlank(message = "provider_code cannot be empty")
  @Size(max = 32, message = "provider_code The length cannot exceed 32")
  private String providerCode;

  @NotNull(message = "model_type cannot be empty")
  private AiModelType modelType;

  @Size(max = 512, message = "description The length cannot exceed 512")
  private String description;

  private List<String> tags;

  @Min(value = 1, message = "context_tokens must be greater than 0")
  private Integer contextTokens;

  @DecimalMin(value = "0", message = "input_price_usd cannot be less than 0")
  private BigDecimal inputPriceUsd;

  @DecimalMin(value = "0", message = "output_price_usd cannot be less than 0")
  private BigDecimal outputPriceUsd;

  @Min(value = 1, message = "embedding_dimension must be greater than 0")
  private Integer embeddingDimension;

  @Size(max = 255, message = "base_url The length cannot exceed 255")
  private String baseUrl;

  @Size(max = 2048, message = "api_key The length cannot exceed 2048")
  private String apiKey;
}
