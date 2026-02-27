package com.sunny.datapillar.studio.dto.llm.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Schema(name = "LlmUserModelUsageResponse")
public class LlmUserModelUsageResponse {

    private Long id;

    private Long userId;

    private Long aiModelId;

    private String providerModelId;

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
