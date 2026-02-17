package com.sunny.datapillar.studio.module.llm.service.impl;

import com.sunny.datapillar.studio.module.llm.dto.LlmManagerDto;
import com.sunny.datapillar.studio.module.llm.service.LlmConnectionService;
import com.sunny.datapillar.studio.module.llm.service.LlmManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 大模型Connection服务实现
 * 实现大模型Connection业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class LlmConnectionServiceImpl implements LlmConnectionService {

    private final LlmManagerService llmManagerService;

    @Override
    public LlmManagerDto.ConnectResponse connectModel(Long userId, Long modelId, LlmManagerDto.ConnectRequest request) {
        return llmManagerService.connectModel(userId, modelId, request);
    }
}
