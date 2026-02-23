package com.sunny.datapillar.studio.module.llm.service;

import com.sunny.datapillar.studio.module.llm.dto.LlmManagerDto;
import com.sunny.datapillar.studio.module.llm.dto.LlmProviderDto;
import com.sunny.datapillar.studio.module.llm.enums.AiModelType;
import java.util.List;

/**
 * 大模型管理服务
 * 提供大模型管理业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface LlmAdminService {

    List<LlmManagerDto.ProviderResponse> listProviders();

    void createProvider(Long userId, LlmProviderDto.CreateRequest request);

    void updateProvider(Long userId, String providerCode, LlmProviderDto.UpdateRequest request);

    void deleteProvider(Long userId, String providerCode);

    List<LlmManagerDto.ModelResponse> listModels(String keyword,
                                                  String providerCode,
                                                  AiModelType modelType,
                                                  Long userId);

    LlmManagerDto.ModelResponse getModel(Long userId, Long modelId);

    LlmManagerDto.ModelResponse createModel(Long userId, LlmManagerDto.CreateRequest request);

    LlmManagerDto.ModelResponse updateModel(Long userId, Long modelId, LlmManagerDto.UpdateRequest request);

    void deleteModel(Long userId, Long modelId);

    List<LlmManagerDto.ModelUsageResponse> listUserModelUsages(Long operatorUserId,
                                                                Long targetUserId,
                                                                boolean onlyEnabled);

    LlmManagerDto.ModelUsageResponse upsertUserModelGrant(Long operatorUserId,
                                                           Long targetUserId,
                                                           Long modelId,
                                                           LlmManagerDto.ModelGrantRequest request);

    void deleteUserModelGrant(Long operatorUserId, Long targetUserId, Long modelId);
}
