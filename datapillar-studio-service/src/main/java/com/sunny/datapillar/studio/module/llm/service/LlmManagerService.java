package com.sunny.datapillar.studio.module.llm.service;

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
import java.util.List;

/**
 * 大模型Manager服务
 * 提供大模型Manager业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface LlmManagerService {

    List<LlmProviderResponse> listProviders();

    void createProvider(Long userId, LlmProviderCreateRequest request);

    void updateProvider(Long userId, String providerCode, LlmProviderUpdateRequest request);

    void deleteProvider(Long userId, String providerCode);

    List<LlmModelResponse> listModels(String keyword,
                                          String providerCode,
                                          AiModelType modelType,
                                          Long userId);

    LlmModelResponse getModel(Long userId, Long aiModelId);

    LlmModelResponse createModel(Long userId, LlmModelCreateRequest request);

    LlmModelResponse updateModel(Long userId, Long aiModelId, LlmModelUpdateRequest request);

    void deleteModel(Long userId, Long aiModelId);

    LlmModelConnectResponse connectModel(Long userId, Long aiModelId, LlmModelConnectRequest request);

    List<LlmUserModelUsageResponse> listUserModelUsages(Long operatorUserId,
                                                                 Long targetUserId,
                                                                 boolean onlyEnabled);

    List<LlmUserModelPermissionResponse> listUserModelPermissions(Long operatorUserId,
                                                                            Long targetUserId,
                                                                            boolean onlyEnabled);

    void upsertUserModelGrant(Long operatorUserId,
                              Long targetUserId,
                              Long aiModelId,
                              LlmUserModelGrantRequest request);

    void deleteUserModelGrant(Long operatorUserId, Long targetUserId, Long aiModelId);

    LlmUserModelUsageResponse setUserDefaultModel(Long operatorUserId, Long targetUserId, Long aiModelId);
}
