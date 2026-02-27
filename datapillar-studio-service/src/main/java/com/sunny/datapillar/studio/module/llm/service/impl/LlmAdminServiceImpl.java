package com.sunny.datapillar.studio.module.llm.service.impl;

import com.sunny.datapillar.studio.dto.llm.request.*;
import com.sunny.datapillar.studio.dto.llm.response.*;
import com.sunny.datapillar.studio.dto.project.request.*;
import com.sunny.datapillar.studio.dto.project.response.*;
import com.sunny.datapillar.studio.dto.setup.request.*;
import com.sunny.datapillar.studio.dto.setup.response.*;
import com.sunny.datapillar.studio.dto.sql.request.*;
import com.sunny.datapillar.studio.dto.sql.response.*;
import com.sunny.datapillar.studio.dto.tenant.request.*;
import com.sunny.datapillar.studio.dto.tenant.response.*;
import com.sunny.datapillar.studio.dto.user.request.*;
import com.sunny.datapillar.studio.dto.user.response.*;
import com.sunny.datapillar.studio.dto.workflow.request.*;
import com.sunny.datapillar.studio.dto.workflow.response.*;
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
    public List<LlmProviderResponse> listProviders() {
        return llmManagerService.listProviders();
    }

    @Override
    public void createProvider(Long userId, LlmProviderCreateRequest request) {
        llmManagerService.createProvider(userId, request);
    }

    @Override
    public void updateProvider(Long userId, String providerCode, LlmProviderUpdateRequest request) {
        llmManagerService.updateProvider(userId, providerCode, request);
    }

    @Override
    public void deleteProvider(Long userId, String providerCode) {
        llmManagerService.deleteProvider(userId, providerCode);
    }

    @Override
    public List<LlmModelResponse> listModels(String keyword,
                                                 String providerCode,
                                                 AiModelType modelType,
                                                 Long userId) {
        return llmManagerService.listModels(keyword, providerCode, modelType, userId);
    }

    @Override
    public LlmModelResponse getModel(Long userId, Long aiModelId) {
        return llmManagerService.getModel(userId, aiModelId);
    }

    @Override
    public LlmModelResponse createModel(Long userId, LlmModelCreateRequest request) {
        return llmManagerService.createModel(userId, request);
    }

    @Override
    public LlmModelResponse updateModel(Long userId, Long aiModelId, LlmModelUpdateRequest request) {
        return llmManagerService.updateModel(userId, aiModelId, request);
    }

    @Override
    public void deleteModel(Long userId, Long aiModelId) {
        llmManagerService.deleteModel(userId, aiModelId);
    }

    @Override
    public List<LlmUserModelPermissionResponse> listUserModelPermissions(Long operatorUserId,
                                                                                   Long targetUserId,
                                                                                   boolean onlyEnabled) {
        return llmManagerService.listUserModelPermissions(operatorUserId, targetUserId, onlyEnabled);
    }

    @Override
    public void upsertUserModelGrant(Long operatorUserId,
                                     Long targetUserId,
                                     Long aiModelId,
                                     LlmUserModelGrantRequest request) {
        llmManagerService.upsertUserModelGrant(operatorUserId, targetUserId, aiModelId, request);
    }

    @Override
    public void deleteUserModelGrant(Long operatorUserId, Long targetUserId, Long aiModelId) {
        llmManagerService.deleteUserModelGrant(operatorUserId, targetUserId, aiModelId);
    }
}
