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

    @NotBlank(message = "provider_model_id 不能为空")
    @Size(max = 128, message = "provider_model_id 长度不能超过 128")
    private String providerModelId;

    @NotBlank(message = "name 不能为空")
    @Size(max = 128, message = "name 长度不能超过 128")
    private String name;

    @NotBlank(message = "provider_code 不能为空")
    @Size(max = 32, message = "provider_code 长度不能超过 32")
    private String providerCode;

    @NotNull(message = "model_type 不能为空")
    private AiModelType modelType;

    @Size(max = 512, message = "description 长度不能超过 512")
    private String description;

    private List<String> tags;

    @Min(value = 1, message = "context_tokens 必须大于 0")
    private Integer contextTokens;

    @DecimalMin(value = "0", message = "input_price_usd 不能小于 0")
    private BigDecimal inputPriceUsd;

    @DecimalMin(value = "0", message = "output_price_usd 不能小于 0")
    private BigDecimal outputPriceUsd;

    @Min(value = 1, message = "embedding_dimension 必须大于 0")
    private Integer embeddingDimension;

    @Size(max = 255, message = "base_url 长度不能超过 255")
    private String baseUrl;

    @Size(max = 2048, message = "api_key 长度不能超过 2048")
    private String apiKey;
}
