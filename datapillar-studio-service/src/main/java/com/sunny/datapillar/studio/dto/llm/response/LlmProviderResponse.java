package com.sunny.datapillar.studio.dto.llm.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "LlmProviderResponse")
public class LlmProviderResponse {

    private Long id;

    private String code;

    private String name;

    private String baseUrl;

    private List<String> modelIds;
}
