package com.sunny.datapillar.studio.dto.setup.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "SetupInitializeResponse")
public class SetupInitializeResponse {

    private Long tenantId;

    private Long userId;
}
