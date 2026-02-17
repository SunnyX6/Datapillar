package com.sunny.datapillar.studio.module.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 大模型Usage数据传输对象
 * 定义大模型Usage数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class LlmUsageDto {

    @Schema(name = "LlmUsageModelUsageResponse")
    public static class ModelUsageResponse extends LlmManagerDto.ModelUsageResponse {
    }
}
