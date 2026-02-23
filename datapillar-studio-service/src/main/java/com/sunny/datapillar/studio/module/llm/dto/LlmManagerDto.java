package com.sunny.datapillar.studio.module.llm.dto;

import com.sunny.datapillar.studio.module.llm.enums.AiModelType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 大模型Manager数据传输对象
 * 定义大模型Manager数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class LlmManagerDto {

    @Data
    @Schema(name = "LlmManagerProviderResponse")
    public static class ProviderResponse {
        private Long id;
        private String code;
        private String name;
        private String baseUrl;
        private List<String> modelIds;
    }

    @Data
    @Schema(name = "LlmManagerModelResponse")
    public static class ModelResponse {
        private Long id;
        private String modelId;
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

    @Data
    @Schema(name = "LlmManagerModelUsageResponse")
    public static class ModelUsageResponse {
        private Long id;
        private Long userId;
        private Long aiModelId;
        private String modelId;
        private String modelName;
        private String modelType;
        private String modelStatus;
        private Long providerId;
        private String providerCode;
        private String providerName;
        private Long permissionId;
        private String permissionCode;
        private Integer permissionLevel;
        private Boolean isDefault;
        private String callCount;
        private String promptTokens;
        private String completionTokens;
        private String totalTokens;
        private String totalCostUsd;
        private Long grantedBy;
        private LocalDateTime grantedAt;
        private Long updatedBy;
        private LocalDateTime expiresAt;
        private LocalDateTime lastUsedAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Schema(name = "LlmManagerModelGrantRequest")
    public static class ModelGrantRequest {
        @NotBlank(message = "permission_code 不能为空")
        @Size(max = 32, message = "permission_code 长度不能超过 32")
        private String permissionCode;

        private Boolean isDefault;

        private LocalDateTime expiresAt;
    }

    @Data
    @Schema(name = "LlmManagerCreateRequest")
    public static class CreateRequest {
        @NotBlank(message = "model_id 不能为空")
        @Size(max = 128, message = "model_id 长度不能超过 128")
        private String modelId;

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
        private java.math.BigDecimal inputPriceUsd;

        @DecimalMin(value = "0", message = "output_price_usd 不能小于 0")
        private java.math.BigDecimal outputPriceUsd;

        @Min(value = 1, message = "embedding_dimension 必须大于 0")
        private Integer embeddingDimension;

        @Size(max = 255, message = "base_url 长度不能超过 255")
        private String baseUrl;

        @Size(max = 2048, message = "api_key 长度不能超过 2048")
        private String apiKey;
    }

    @Data
    @Schema(name = "LlmManagerUpdateRequest")
    public static class UpdateRequest {
        @Size(min = 1, max = 128, message = "name 长度范围必须在 1-128")
        private String name;

        @Size(max = 512, message = "description 长度不能超过 512")
        private String description;

        private List<String> tags;

        @Min(value = 1, message = "context_tokens 必须大于 0")
        private Integer contextTokens;

        @DecimalMin(value = "0", message = "input_price_usd 不能小于 0")
        private java.math.BigDecimal inputPriceUsd;

        @DecimalMin(value = "0", message = "output_price_usd 不能小于 0")
        private java.math.BigDecimal outputPriceUsd;

        @Min(value = 1, message = "embedding_dimension 必须大于 0")
        private Integer embeddingDimension;

        @Size(max = 255, message = "base_url 长度不能超过 255")
        private String baseUrl;
    }

    @Data
    @Schema(name = "LlmManagerConnectRequest")
    public static class ConnectRequest {
        @NotBlank(message = "api_key 不能为空")
        private String apiKey;

        @Size(max = 255, message = "base_url 长度不能超过 255")
        private String baseUrl;
    }

    @Data
    @Schema(name = "LlmManagerConnectResponse")
    public static class ConnectResponse {
        private boolean connected;
        private boolean hasApiKey;
    }
}
