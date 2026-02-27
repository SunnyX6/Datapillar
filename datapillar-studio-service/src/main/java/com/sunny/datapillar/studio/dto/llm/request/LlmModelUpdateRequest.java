package com.sunny.datapillar.studio.dto.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "LlmModelUpdateRequest")
public class LlmModelUpdateRequest {

    @Size(min = 1, max = 128, message = "name 长度范围必须在 1-128")
    private String name;

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
}
