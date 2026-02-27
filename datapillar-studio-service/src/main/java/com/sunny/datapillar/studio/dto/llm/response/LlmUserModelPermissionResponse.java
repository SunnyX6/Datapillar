package com.sunny.datapillar.studio.dto.llm.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "LlmUserModelPermissionResponse")
public class LlmUserModelPermissionResponse {

    private Long aiModelId;

    private String providerModelId;

    private String modelName;

    private String modelType;

    private String modelStatus;

    private Long providerId;

    private String providerCode;

    private String providerName;

    private String permissionCode;

    private Boolean isDefault;
}
