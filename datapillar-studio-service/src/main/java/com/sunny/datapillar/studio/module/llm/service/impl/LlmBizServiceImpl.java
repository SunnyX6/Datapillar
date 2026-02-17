package com.sunny.datapillar.studio.module.llm.service.impl;

import com.sunny.datapillar.studio.module.llm.dto.LlmManagerDto;
import com.sunny.datapillar.studio.module.llm.service.LlmBizService;
import com.sunny.datapillar.studio.module.llm.service.LlmManagerService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 大模型业务服务实现
 * 实现大模型业务业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class LlmBizServiceImpl implements LlmBizService {

    private final LlmManagerService llmManagerService;

    @Override
    public List<LlmManagerDto.ModelUsageResponse> listCurrentUserModelUsages(Long currentUserId,
                                                                               boolean onlyEnabled) {
        return llmManagerService.listUserModelUsages(currentUserId, currentUserId, onlyEnabled);
    }

    @Override
    public LlmManagerDto.ModelUsageResponse setCurrentUserDefaultModel(Long currentUserId, Long modelId) {
        return llmManagerService.setUserDefaultModel(currentUserId, currentUserId, modelId);
    }
}
