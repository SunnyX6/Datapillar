package com.sunny.datapillar.studio.module.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 大模型Model数据传输对象
 * 定义大模型Model数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class LlmModelDto {

    @Schema(name = "LlmModelCreateRequest")
    public static class CreateRequest extends LlmManagerDto.CreateRequest {
    }

    @Schema(name = "LlmModelUpdateRequest")
    public static class UpdateRequest extends LlmManagerDto.UpdateRequest {
    }

    @Schema(name = "LlmModelResponse")
    public static class Response extends LlmManagerDto.ModelResponse {
    }
}
