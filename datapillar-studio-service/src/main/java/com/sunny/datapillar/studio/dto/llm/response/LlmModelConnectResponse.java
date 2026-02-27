package com.sunny.datapillar.studio.dto.llm.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "LlmModelConnectResponse")
public class LlmModelConnectResponse {

    private boolean connected;

    private boolean hasApiKey;
}
