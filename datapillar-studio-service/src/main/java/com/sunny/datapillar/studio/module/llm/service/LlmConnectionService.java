package com.sunny.datapillar.studio.module.llm.service;

import com.sunny.datapillar.studio.module.llm.dto.LlmManagerDto;

/**
 * 大模型Connection服务
 * 提供大模型Connection业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface LlmConnectionService {

    LlmManagerDto.ConnectResponse connectModel(Long userId, Long modelId, LlmManagerDto.ConnectRequest request);
}
