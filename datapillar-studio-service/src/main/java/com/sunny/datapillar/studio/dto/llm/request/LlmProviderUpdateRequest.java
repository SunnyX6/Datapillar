package com.sunny.datapillar.studio.dto.llm.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
@Schema(name = "LlmProviderUpdateRequest")
public class LlmProviderUpdateRequest {

    @Size(min = 1, max = 64, message = "name 长度范围必须在 1-64")
    private String name;

    @Size(max = 255, message = "base_url 长度不能超过 255")
    private String baseUrl;

    @Size(max = 200, message = "add_model_ids 数量不能超过 200")
    private List<@NotBlank(message = "add_model_ids 存在空值")
            @Size(max = 128, message = "add_model_ids 元素长度不能超过 128") String> addModelIds;

    @Size(max = 200, message = "remove_model_ids 数量不能超过 200")
    private List<@NotBlank(message = "remove_model_ids 存在空值")
            @Size(max = 128, message = "remove_model_ids 元素长度不能超过 128") String> removeModelIds;
}
