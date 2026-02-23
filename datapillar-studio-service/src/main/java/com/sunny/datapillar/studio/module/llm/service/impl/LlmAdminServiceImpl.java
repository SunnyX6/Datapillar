package com.sunny.datapillar.studio.module.llm.service.impl;

import com.sunny.datapillar.studio.module.llm.dto.LlmManagerDto;
import com.sunny.datapillar.studio.module.llm.dto.LlmProviderDto;
import com.sunny.datapillar.studio.module.llm.enums.AiModelType;
import com.sunny.datapillar.studio.module.llm.service.LlmAdminService;
import com.sunny.datapillar.studio.module.llm.service.LlmManagerService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 大模型管理服务实现
 * 实现大模型管理业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class LlmAdminServiceImpl implements LlmAdminService {

    private final LlmManagerService llmManagerService;

    @Override
    public List<LlmManagerDto.ProviderResponse> listProviders() {
        return llmManagerService.listProviders();
    }

    @Override
    public void createProvider(Long userId, LlmProviderDto.CreateRequest request) {
        llmManagerService.createProvider(userId, request);
    }

    @Override
    public void updateProvider(Long userId, String providerCode, LlmProviderDto.UpdateRequest request) {
        llmManagerService.updateProvider(userId, providerCode, request);
    }

    @Override
    public void deleteProvider(Long userId, String providerCode) {
        llmManagerService.deleteProvider(userId, providerCode);
    }

    @Override
    public List<LlmManagerDto.ModelResponse> listModels(String keyword,
                                                         String providerCode,
                                                         AiModelType modelType,
                                                         Long userId) {
        return llmManagerService.listModels(keyword, providerCode, modelType, userId);
    }

    @Override
    public LlmManagerDto.ModelResponse getModel(Long userId, Long modelId) {
        return llmManagerService.getModel(userId, modelId);
    }

    @Override
    public LlmManagerDto.ModelResponse createModel(Long userId, LlmManagerDto.CreateRequest request) {
        return llmManagerService.createModel(userId, request);
    }

    @Override
    public LlmManagerDto.ModelResponse updateModel(Long userId, Long modelId, LlmManagerDto.UpdateRequest request) {
        return llmManagerService.updateModel(userId, modelId, request);
    }

    @Override
    public void deleteModel(Long userId, Long modelId) {
        llmManagerService.deleteModel(userId, modelId);
    }

    @Override
    public List<LlmManagerDto.ModelUsageResponse> listUserModelUsages(Long operatorUserId,
                                                                        Long targetUserId,
                                                                        boolean onlyEnabled) {
        return llmManagerService.listUserModelUsages(operatorUserId, targetUserId, onlyEnabled);
    }

    @Override
    public LlmManagerDto.ModelUsageResponse upsertUserModelGrant(Long operatorUserId,
                                                                  Long targetUserId,
                                                                  Long modelId,
                                                                  LlmManagerDto.ModelGrantRequest request) {
        return llmManagerService.upsertUserModelGrant(operatorUserId, targetUserId, modelId, request);
    }

    @Override
    public void deleteUserModelGrant(Long operatorUserId, Long targetUserId, Long modelId) {
        llmManagerService.deleteUserModelGrant(operatorUserId, targetUserId, modelId);
    }
}
