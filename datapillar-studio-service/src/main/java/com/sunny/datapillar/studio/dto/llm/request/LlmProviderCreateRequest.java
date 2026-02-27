package com.sunny.datapillar.studio.dto.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(name = "LlmProviderCreateRequest")
public class LlmProviderCreateRequest {

    @NotBlank(message = "code 不能为空")
    @Size(max = 32, message = "code 长度不能超过 32")
    private String code;

    @Size(max = 64, message = "name 长度不能超过 64")
    private String name;

    @Size(max = 255, message = "base_url 长度不能超过 255")
    private String baseUrl;
}
