package com.sunny.datapillar.studio.dto.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "LlmModelConnectRequest")
public class LlmModelConnectRequest {

    @NotBlank(message = "api_key 不能为空")
    private String apiKey;

    @Size(max = 255, message = "base_url 长度不能超过 255")
    private String baseUrl;
}
