package com.sunny.datapillar.studio.module.llm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 大模型Connection数据传输对象
 * 定义大模型Connection数据传输结构
 *
 * @author Sunny
 * @date 2026-01-01
 */
public class LlmConnectionDto {

    @Schema(name = "LlmConnectionConnectRequest")
    public static class ConnectRequest extends LlmManagerDto.ConnectRequest {
    }

    @Schema(name = "LlmConnectionConnectResponse")
    public static class ConnectResponse extends LlmManagerDto.ConnectResponse {
    }
}
