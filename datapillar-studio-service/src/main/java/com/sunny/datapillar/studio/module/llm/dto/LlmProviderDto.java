package com.sunny.datapillar.studio.module.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 大模型提供器数据传输对象
 * 定义大模型提供器数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class LlmProviderDto {

    @Schema(name = "LlmProviderResponse")
    public static class Response extends LlmManagerDto.ProviderResponse {
    }
}
