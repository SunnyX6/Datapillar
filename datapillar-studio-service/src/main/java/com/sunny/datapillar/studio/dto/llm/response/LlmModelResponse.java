package com.sunny.datapillar.studio.dto.llm.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "LlmModelResponse")
public class LlmModelResponse {

    private Long aiModelId;

    private String providerModelId;

    private String name;

    private Long providerId;

    private String providerCode;

    private String providerName;

    private String modelType;

    private String description;

    private List<String> tags;

    private Integer contextTokens;

    private String inputPriceUsd;

    private String outputPriceUsd;

    private Integer embeddingDimension;

    private String baseUrl;

    private String maskedApiKey;

    private String status;

    private Boolean hasApiKey;

    private Long createdBy;

    private Long updatedBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
