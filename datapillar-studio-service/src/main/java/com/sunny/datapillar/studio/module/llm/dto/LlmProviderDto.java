package com.sunny.datapillar.studio.module.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 大模型提供器数据传输对象
 * 定义大模型提供器数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class LlmProviderDto {

    @Data
    @Schema(name = "LlmProviderCreateRequest")
    public static class CreateRequest {

        @NotBlank(message = "code 不能为空")
        @Size(max = 32, message = "code 长度不能超过 32")
        private String code;

        @Size(max = 64, message = "name 长度不能超过 64")
        private String name;

        @Size(max = 255, message = "base_url 长度不能超过 255")
        private String baseUrl;
    }

    @Data
    @Schema(name = "LlmProviderUpdateRequest")
    public static class UpdateRequest {

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

    @Schema(name = "LlmProviderResponse")
    public static class Response extends LlmManagerDto.ProviderResponse {
    }
}
