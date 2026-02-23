package com.sunny.datapillar.studio.module.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.llm.dto.LlmManagerDto;
import com.sunny.datapillar.studio.module.llm.entity.AiModelGrant;
import com.sunny.datapillar.studio.module.llm.dto.LlmProviderDto;
import com.sunny.datapillar.studio.module.llm.entity.AiModel;
import com.sunny.datapillar.studio.module.llm.entity.AiProvider;
import com.sunny.datapillar.studio.module.llm.entity.AiUsage;
import com.sunny.datapillar.studio.module.llm.enums.AiModelStatus;
import com.sunny.datapillar.studio.module.llm.enums.AiModelType;
import com.sunny.datapillar.studio.module.llm.mapper.AiModelGrantMapper;
import com.sunny.datapillar.studio.module.llm.mapper.AiModelMapper;
import com.sunny.datapillar.studio.module.llm.mapper.AiProviderMapper;
import com.sunny.datapillar.studio.module.llm.mapper.AiUsageMapper;
import com.sunny.datapillar.studio.module.tenant.entity.Permission;
import com.sunny.datapillar.studio.module.tenant.mapper.PermissionMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantCodeResolver;
import com.sunny.datapillar.studio.module.llm.service.LlmManagerService;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoRpcClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.InternalException;

/**
 * 大模型Manager服务实现
 * 实现大模型Manager业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class LlmManagerServiceImpl implements LlmManagerService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private static final String OPENAI_COMPLETIONS_PATH = "/chat/completions";
    private static final String OPENAI_EMBEDDINGS_PATH = "/embeddings";

    private static final String PERMISSION_DISABLE = "DISABLE";
    private static final String PERMISSION_READ = "READ";
    private static final String PERMISSION_ADMIN = "ADMIN";
    private static final int API_KEY_MASK_VISIBLE_LENGTH = 4;
    private static final String MASK_FALLBACK = "******";
    private static final Set<String> OPENAI_COMPATIBLE_PROVIDERS = Set.of(
            "openai", "deepseek", "openrouter", "ollama", "glm", "mistral", "google", "meta"
    );

    private final AiProviderMapper aiProviderMapper;
    private final AiModelMapper aiModelMapper;
    private final AiModelGrantMapper aiModelGrantMapper;
    private final AiUsageMapper aiUsageMapper;
    private final PermissionMapper permissionMapper;
    private final UserMapper userMapper;
    private final AuthCryptoRpcClient authCryptoClient;
    private final TenantCodeResolver tenantCodeResolver;
    private final ObjectMapper objectMapper;

    @Override
    public List<LlmManagerDto.ProviderResponse> listProviders() {
        LambdaQueryWrapper<AiProvider> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AiProvider::getId);
        List<AiProvider> providers = aiProviderMapper.selectList(wrapper);
        List<LlmManagerDto.ProviderResponse> result = new ArrayList<>(providers.size());
        for (AiProvider provider : providers) {
            result.add(toProviderResponse(provider));
        }
        return result;
    }

    @Override
    @Transactional
    public void createProvider(Long userId, LlmProviderDto.CreateRequest request) {
        getRequiredUserId(userId);
        if (request == null) {
            throw new BadRequestException("参数错误");
        }
        String providerCode = normalizeProviderCode(request.getCode());
        AiProvider existing = getProviderByCode(providerCode);
        if (existing != null) {
            return;
        }

        AiProvider provider = new AiProvider();
        provider.setCode(providerCode);
        provider.setName(resolveProviderName(request.getName(), providerCode));
        provider.setBaseUrl(normalizeNullableText(request.getBaseUrl()));
        provider.setModelIds(writeJsonList(Collections.emptyList()));

        try {
            int inserted = aiProviderMapper.insert(provider);
            if (inserted == 0 || provider.getId() == null) {
                throw new InternalException("服务器内部错误");
            }
        } catch (DuplicateKeyException ex) {
            AiProvider duplicated = getProviderByCode(providerCode);
            if (duplicated != null) {
                return;
            }
            throw ex;
        }
    }

    @Override
    @Transactional
    public void updateProvider(Long userId,
                               String providerCode,
                               LlmProviderDto.UpdateRequest request) {
        getRequiredUserId(userId);
        if (request == null) {
            throw new BadRequestException("参数错误");
        }
        String normalizedProviderCode = normalizeProviderCode(providerCode);
        AiProvider provider = getProviderByCode(normalizedProviderCode);
        if (provider == null) {
            throw new NotFoundException("供应商不存在");
        }

        boolean updated = false;
        if (request.getName() != null) {
            provider.setName(normalizeRequiredText(request.getName(), "name 不能为空"));
            updated = true;
        }
        if (request.getBaseUrl() != null) {
            provider.setBaseUrl(normalizeNullableText(request.getBaseUrl()));
            updated = true;
        }

        List<String> addModelIds = normalizeIncomingModelIds(request.getAddModelIds());
        List<String> removeModelIds = normalizeIncomingModelIds(request.getRemoveModelIds());
        if (!addModelIds.isEmpty() || !removeModelIds.isEmpty()) {
            Set<String> intersection = new HashSet<>(addModelIds);
            intersection.retainAll(removeModelIds);
            if (!intersection.isEmpty()) {
                throw new BadRequestException("add_model_ids 与 remove_model_ids 不能有重复值");
            }
            List<String> currentModelIds = normalizeStoredModelIds(parseJsonList(provider.getModelIds()));
            LinkedHashSet<String> mergedModelIds = new LinkedHashSet<>(currentModelIds);
            mergedModelIds.removeAll(removeModelIds);
            mergedModelIds.addAll(addModelIds);
            provider.setModelIds(writeJsonList(new ArrayList<>(mergedModelIds)));
            updated = true;
        }

        if (!updated) {
            throw new BadRequestException("没有可更新字段");
        }

        int affected = aiProviderMapper.updateById(provider);
        if (affected == 0) {
            throw new InternalException("服务器内部错误");
        }
    }

    @Override
    @Transactional
    public void deleteProvider(Long userId, String providerCode) {
        getRequiredUserId(userId);
        String normalizedProviderCode = normalizeProviderCode(providerCode);
        AiProvider provider = getProviderByCode(normalizedProviderCode);
        if (provider == null) {
            throw new NotFoundException("供应商不存在");
        }

        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModel::getProviderId, provider.getId());
        Long modelCount = aiModelMapper.selectCount(wrapper);
        if (modelCount != null && modelCount > 0) {
            throw new BadRequestException("供应商下存在模型，不能删除");
        }

        int affected = aiProviderMapper.deleteById(provider.getId());
        if (affected == 0) {
            throw new InternalException("服务器内部错误");
        }
    }

    @Override
    public List<LlmManagerDto.ModelResponse> listModels(String keyword,
                                                        String providerCode,
                                                        AiModelType modelType,
                                                        Long userId) {
        getRequiredUserId(userId);
        Long tenantId = getRequiredTenantId();

        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModel::getTenantId, tenantId);
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(w -> w.like(AiModel::getModelId, normalizedKeyword)
                    .or()
                    .like(AiModel::getName, normalizedKeyword));
        }

        if (StringUtils.hasText(providerCode)) {
            AiProvider provider = getProviderByCode(normalizeProviderCode(providerCode));
            if (provider == null) {
                return Collections.emptyList();
            }
            wrapper.eq(AiModel::getProviderId, provider.getId());
        }

        if (modelType != null) {
            wrapper.eq(AiModel::getModelType, modelType);
        }

        wrapper.orderByDesc(AiModel::getUpdatedAt)
                .orderByDesc(AiModel::getCreatedAt)
                .orderByDesc(AiModel::getId);

        List<AiModel> models = aiModelMapper.selectList(wrapper);
        Map<Long, AiProvider> providers = loadProviders(models);
        String tenantCode = tenantCodeResolver.requireTenantCode(tenantId);
        List<LlmManagerDto.ModelResponse> rows = new ArrayList<>(models.size());
        for (AiModel model : models) {
            rows.add(toModelResponse(model, providers.get(model.getProviderId()), tenantCode));
        }
        return rows;
    }

    @Override
    public LlmManagerDto.ModelResponse getModel(Long userId, Long modelId) {
        getRequiredUserId(userId);
        Long tenantId = getRequiredTenantId();
        AiModel model = getModelOrThrow(modelId, tenantId);
        AiProvider provider = aiProviderMapper.selectById(model.getProviderId());
        String tenantCode = tenantCodeResolver.requireTenantCode(tenantId);
        return toModelResponse(model, provider, tenantCode);
    }

    @Override
    @Transactional
    public LlmManagerDto.ModelResponse createModel(Long userId, LlmManagerDto.CreateRequest request) {
        if (request == null) {
            throw new BadRequestException("参数错误");
        }
        Long tenantId = getRequiredTenantId();
        String tenantCode = tenantCodeResolver.requireTenantCode(tenantId);
        Long operatorUserId = getRequiredUserId(userId);

        String providerCode = normalizeProviderCode(request.getProviderCode());
        AiProvider provider = getProviderByCode(providerCode);
        if (provider == null) {
            throw new IllegalArgumentException("供应商不存在");
        }

        String modelId = normalizeRequiredText(request.getModelId(), "model_id 不能为空");
        if (existsModelByModelId(tenantId, modelId)) {
            throw new AlreadyExistsException("资源已存在");
        }

        if (request.getModelType() == AiModelType.EMBEDDINGS && request.getEmbeddingDimension() == null) {
            throw new IllegalArgumentException("embedding_dimension 不能为空");
        }

        AiModel model = new AiModel();
        model.setTenantId(tenantId);
        model.setModelId(modelId);
        model.setName(normalizeRequiredText(request.getName(), "name 不能为空"));
        model.setProviderId(provider.getId());
        model.setModelType(request.getModelType());
        model.setDescription(normalizeNullableText(request.getDescription()));
        model.setTags(writeJsonList(request.getTags()));
        model.setContextTokens(request.getContextTokens());
        model.setInputPriceUsd(request.getInputPriceUsd());
        model.setOutputPriceUsd(request.getOutputPriceUsd());
        model.setEmbeddingDimension(request.getEmbeddingDimension());
        String resolvedBaseUrl = normalizeNullableText(request.getBaseUrl());
        String normalizedApiKey = normalizeNullableText(request.getApiKey());
        if (StringUtils.hasText(normalizedApiKey)) {
            if (!StringUtils.hasText(resolvedBaseUrl)) {
                resolvedBaseUrl = normalizeNullableText(provider.getBaseUrl());
            }
            if (!StringUtils.hasText(resolvedBaseUrl)) {
                throw new IllegalArgumentException("base_url 不能为空");
            }
            verifyModelConnection(provider.getCode(), model.getModelId(), model.getModelType(), normalizedApiKey, resolvedBaseUrl);
            model.setApiKey(authCryptoClient.encryptLlmApiKey(tenantCode, normalizedApiKey));
            model.setStatus(AiModelStatus.ACTIVE);
        } else {
            model.setApiKey(null);
            model.setStatus(AiModelStatus.CONNECT);
        }
        model.setBaseUrl(resolvedBaseUrl);
        model.setCreatedBy(operatorUserId);
        model.setUpdatedBy(operatorUserId);

        int inserted = aiModelMapper.insert(model);
        if (inserted == 0 || model.getId() == null) {
            throw new InternalException("服务器内部错误");
        }
        return toModelResponse(model, provider, tenantCode);
    }

    @Override
    @Transactional
    public LlmManagerDto.ModelResponse updateModel(Long userId, Long modelId, LlmManagerDto.UpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("没有可更新字段");
        }
        Long tenantId = getRequiredTenantId();
        Long operatorUserId = getRequiredUserId(userId);
        AiModel model = getModelOrThrow(modelId, tenantId);

        boolean updated = false;
        if (request.getName() != null) {
            model.setName(normalizeRequiredText(request.getName(), "name 不能为空"));
            updated = true;
        }
        if (request.getDescription() != null) {
            model.setDescription(normalizeNullableText(request.getDescription()));
            updated = true;
        }
        if (request.getTags() != null) {
            model.setTags(writeJsonList(request.getTags()));
            updated = true;
        }
        if (request.getContextTokens() != null) {
            model.setContextTokens(request.getContextTokens());
            updated = true;
        }
        if (request.getInputPriceUsd() != null) {
            model.setInputPriceUsd(request.getInputPriceUsd());
            updated = true;
        }
        if (request.getOutputPriceUsd() != null) {
            model.setOutputPriceUsd(request.getOutputPriceUsd());
            updated = true;
        }
        if (request.getEmbeddingDimension() != null) {
            model.setEmbeddingDimension(request.getEmbeddingDimension());
            updated = true;
        }
        if (request.getBaseUrl() != null) {
            model.setBaseUrl(normalizeNullableText(request.getBaseUrl()));
            updated = true;
        }

        if (!updated) {
            throw new IllegalArgumentException("没有可更新字段");
        }
        if (model.getModelType() == AiModelType.EMBEDDINGS && model.getEmbeddingDimension() == null) {
            throw new IllegalArgumentException("embedding_dimension 不能为空");
        }

        model.setUpdatedBy(operatorUserId);
        int affected = aiModelMapper.updateById(model);
        if (affected == 0) {
            throw new InternalException("服务器内部错误");
        }

        AiProvider provider = aiProviderMapper.selectById(model.getProviderId());
        String tenantCode = tenantCodeResolver.requireTenantCode(tenantId);
        return toModelResponse(model, provider, tenantCode);
    }

    @Override
    @Transactional
    public void deleteModel(Long userId, Long modelId) {
        getRequiredUserId(userId);
        Long tenantId = getRequiredTenantId();
        AiModel model = getModelOrThrow(modelId, tenantId);

        LambdaQueryWrapper<AiModelGrant> grantWrapper = new LambdaQueryWrapper<>();
        grantWrapper.eq(AiModelGrant::getTenantId, tenantId)
                .eq(AiModelGrant::getModelId, model.getId());
        Long grantCount = aiModelGrantMapper.selectCount(grantWrapper);
        if (grantCount != null && grantCount > 0) {
            throw new BadRequestException("模型已授权，不能删除");
        }

        LambdaQueryWrapper<AiUsage> usageWrapper = new LambdaQueryWrapper<>();
        usageWrapper.eq(AiUsage::getTenantId, tenantId)
                .eq(AiUsage::getModelId, model.getId());
        Long usageCount = aiUsageMapper.selectCount(usageWrapper);
        if (usageCount != null && usageCount > 0) {
            throw new BadRequestException("模型存在使用记录，不能删除");
        }

        int deleted = aiModelMapper.deleteById(model.getId());
        if (deleted == 0) {
            throw new InternalException("服务器内部错误");
        }
    }

    @Override
    @Transactional
    public LlmManagerDto.ConnectResponse connectModel(Long userId, Long modelId, LlmManagerDto.ConnectRequest request) {
        if (request == null) {
            throw new BadRequestException("参数错误");
        }
        Long tenantId = getRequiredTenantId();
        Long operatorUserId = getRequiredUserId(userId);
        AiModel model = getModelOrThrow(modelId, tenantId);
        AiProvider provider = aiProviderMapper.selectById(model.getProviderId());
        if (provider == null) {
            throw new IllegalArgumentException("供应商不存在");
        }

        String apiKey = normalizeRequiredText(request.getApiKey(), "api_key 不能为空");
        String resolvedBaseUrl = normalizeNullableText(request.getBaseUrl());
        if (!StringUtils.hasText(resolvedBaseUrl)) {
            resolvedBaseUrl = normalizeNullableText(model.getBaseUrl());
        }
        if (!StringUtils.hasText(resolvedBaseUrl)) {
            throw new IllegalArgumentException("base_url 不能为空");
        }

        verifyModelConnection(provider.getCode(), model.getModelId(), model.getModelType(), apiKey, resolvedBaseUrl);

        String tenantCode = tenantCodeResolver.requireTenantCode(tenantId);
        String encryptedApiKey = authCryptoClient.encryptLlmApiKey(tenantCode, apiKey);

        model.setApiKey(encryptedApiKey);
        model.setBaseUrl(resolvedBaseUrl);
        model.setStatus(AiModelStatus.ACTIVE);
        model.setUpdatedBy(operatorUserId);
        int updated = aiModelMapper.updateById(model);
        if (updated == 0) {
            throw new InternalException("服务器内部错误");
        }

        LlmManagerDto.ConnectResponse response = new LlmManagerDto.ConnectResponse();
        response.setConnected(true);
        response.setHasApiKey(true);
        return response;
    }

    @Override
    public List<LlmManagerDto.ModelUsageResponse> listUserModelUsages(Long operatorUserId,
                                                                       Long targetUserId,
                                                                       boolean onlyEnabled) {
        getRequiredUserId(operatorUserId);
        Long tenantId = getRequiredTenantId();
        requireTenantUserExists(tenantId, targetUserId);
        return listModelUsagesByUser(tenantId, targetUserId, onlyEnabled, getPermissionMapByCode());
    }

    @Override
    @Transactional
    public LlmManagerDto.ModelUsageResponse upsertUserModelGrant(Long operatorUserId,
                                                                  Long targetUserId,
                                                                  Long modelId,
                                                                  LlmManagerDto.ModelGrantRequest request) {
        if (request == null) {
            throw new BadRequestException("参数错误");
        }
        Long tenantId = getRequiredTenantId();
        Long operatorId = getRequiredUserId(operatorUserId);
        requireTenantUserExists(tenantId, targetUserId);
        AiModel model = getModelOrThrow(modelId, tenantId);
        Map<String, Permission> permissionByCode = getPermissionMapByCode();
        Map<Long, Permission> permissionById = toPermissionMapById(permissionByCode.values());
        Permission permission = resolvePermissionByCode(permissionByCode, request.getPermissionCode());

        boolean isDisablePermission = isDisablePermission(permissionByCode, permission.getCode());
        boolean setDefault = Boolean.TRUE.equals(request.getIsDefault()) && !isDisablePermission;
        LocalDateTime expiresAt = request.getExpiresAt();
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("expires_at 必须晚于当前时间");
        }
        if (!isDisablePermission && model.getStatus() != AiModelStatus.ACTIVE) {
            throw new BadRequestException("仅已连接模型可以授权");
        }

        LocalDateTime now = LocalDateTime.now();
        AiModelGrant grant = getGrantNullable(tenantId, targetUserId, modelId);
        if (grant == null) {
            grant = new AiModelGrant();
            grant.setTenantId(tenantId);
            grant.setUserId(targetUserId);
            grant.setModelId(modelId);
        }
        grant.setPermissionId(permission.getId());
        grant.setIsDefault(setDefault);
        grant.setGrantedBy(operatorId);
        grant.setGrantedAt(now);
        grant.setUpdatedBy(operatorId);
        grant.setExpiresAt(expiresAt);

        int affected;
        if (grant.getId() == null) {
            affected = aiModelGrantMapper.insert(grant);
        } else {
            affected = aiModelGrantMapper.updateById(grant);
        }
        if (affected == 0) {
            throw new InternalException("服务器内部错误");
        }

        if (setDefault) {
            clearUserDefaultGrant(tenantId, targetUserId, modelId);
        }

        AiModelGrant refreshed = getGrantOrThrow(tenantId, targetUserId, modelId);
        AiUsage usage = getUsageNullable(tenantId, targetUserId, modelId);
        AiProvider provider = aiProviderMapper.selectById(model.getProviderId());
        return toModelUsageResponse(
                refreshed,
                permissionById.get(refreshed.getPermissionId()),
                usage,
                model,
                provider
        );
    }

    @Override
    @Transactional
    public void deleteUserModelGrant(Long operatorUserId, Long targetUserId, Long modelId) {
        Long tenantId = getRequiredTenantId();
        getRequiredUserId(operatorUserId);
        requireTenantUserExists(tenantId, targetUserId);

        AiModelGrant grant = getGrantOrThrow(tenantId, targetUserId, modelId);
        int deleted = aiModelGrantMapper.deleteById(grant.getId());
        if (deleted == 0) {
            throw new InternalException("服务器内部错误");
        }
    }

    @Override
    @Transactional
    public LlmManagerDto.ModelUsageResponse setUserDefaultModel(Long operatorUserId,
                                                                Long targetUserId,
                                                                Long modelId) {
        Long tenantId = getRequiredTenantId();
        Long operatorId = getRequiredUserId(operatorUserId);
        requireTenantUserExists(tenantId, targetUserId);
        Map<String, Permission> permissionByCode = getPermissionMapByCode();
        Map<Long, Permission> permissionById = toPermissionMapById(permissionByCode.values());

        AiModel model = getModelOrThrow(modelId, tenantId);
        if (model.getStatus() != AiModelStatus.ACTIVE) {
            throw new IllegalArgumentException("仅已连接模型可以设为默认");
        }

        AiModelGrant grant = getGrantOrThrow(tenantId, targetUserId, modelId);
        Permission permission = permissionById.get(grant.getPermissionId());
        if (!isUsablePermission(permissionByCode, permission) || isGrantExpired(grant)) {
            throw new ForbiddenException("无权限访问");
        }

        clearUserDefaultGrant(tenantId, targetUserId, modelId);
        grant.setIsDefault(Boolean.TRUE);
        grant.setUpdatedBy(operatorId);
        int updated = aiModelGrantMapper.updateById(grant);
        if (updated == 0) {
            throw new InternalException("服务器内部错误");
        }

        AiModelGrant refreshed = getGrantOrThrow(tenantId, targetUserId, modelId);
        AiUsage usage = getUsageNullable(tenantId, targetUserId, modelId);
        AiProvider provider = aiProviderMapper.selectById(model.getProviderId());
        return toModelUsageResponse(
                refreshed,
                permissionById.get(refreshed.getPermissionId()),
                usage,
                model,
                provider
        );
    }

    private List<LlmManagerDto.ModelUsageResponse> listModelUsagesByUser(Long tenantId,
                                                                          Long userId,
                                                                          boolean onlyEnabled,
                                                                          Map<String, Permission> permissionByCode) {
        Map<Long, Permission> permissionById = toPermissionMapById(permissionByCode.values());
        LambdaQueryWrapper<AiModelGrant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModelGrant::getTenantId, tenantId)
                .eq(AiModelGrant::getUserId, userId);
        wrapper.orderByDesc(AiModelGrant::getUpdatedAt)
                .orderByDesc(AiModelGrant::getId);

        List<AiModelGrant> grants = aiModelGrantMapper.selectList(wrapper);
        if (grants == null || grants.isEmpty()) {
            return Collections.emptyList();
        }

        List<AiModelGrant> filteredGrants = new ArrayList<>(grants.size());
        Set<Long> modelIds = new HashSet<>();
        for (AiModelGrant grant : grants) {
            Permission permission = permissionById.get(grant.getPermissionId());
            if (onlyEnabled && (!isUsablePermission(permissionByCode, permission) || isGrantExpired(grant))) {
                continue;
            }
            filteredGrants.add(grant);
            modelIds.add(grant.getModelId());
        }
        if (filteredGrants.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, AiModel> modelMap = loadModelsByIds(tenantId, modelIds);
        Map<Long, AiProvider> providerMap = loadProviders(new ArrayList<>(modelMap.values()));
        Map<Long, AiUsage> usageByModelId = loadUsagesByModelIds(tenantId, userId, modelIds);

        List<LlmManagerDto.ModelUsageResponse> rows = new ArrayList<>(filteredGrants.size());
        for (AiModelGrant grant : filteredGrants) {
            AiModel model = modelMap.get(grant.getModelId());
            if (model == null) {
                continue;
            }
            Permission permission = permissionById.get(grant.getPermissionId());
            AiProvider provider = providerMap.get(model.getProviderId());
            AiUsage usage = usageByModelId.get(grant.getModelId());
            rows.add(toModelUsageResponse(grant, permission, usage, model, provider));
        }
        return rows;
    }

    private AiModel getModelOrThrow(Long modelId, Long tenantId) {
        if (modelId == null || modelId <= 0) {
            throw new BadRequestException("参数错误");
        }
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModel::getTenantId, tenantId)
                .eq(AiModel::getId, modelId);
        AiModel model = aiModelMapper.selectOne(wrapper);
        if (model == null) {
            throw new NotFoundException("资源不存在");
        }
        return model;
    }

    private AiModelGrant getGrantOrThrow(Long tenantId, Long userId, Long modelId) {
        AiModelGrant grant = getGrantNullable(tenantId, userId, modelId);
        if (grant == null) {
            throw new NotFoundException("资源不存在");
        }
        return grant;
    }

    private AiModelGrant getGrantNullable(Long tenantId, Long userId, Long modelId) {
        LambdaQueryWrapper<AiModelGrant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModelGrant::getTenantId, tenantId)
                .eq(AiModelGrant::getUserId, userId)
                .eq(AiModelGrant::getModelId, modelId);
        return aiModelGrantMapper.selectOne(wrapper);
    }

    private AiUsage getUsageNullable(Long tenantId, Long userId, Long modelId) {
        LambdaQueryWrapper<AiUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiUsage::getTenantId, tenantId)
                .eq(AiUsage::getUserId, userId)
                .eq(AiUsage::getModelId, modelId);
        return aiUsageMapper.selectOne(wrapper);
    }

    private Map<Long, AiUsage> loadUsagesByModelIds(Long tenantId, Long userId, Set<Long> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<AiUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiUsage::getTenantId, tenantId)
                .eq(AiUsage::getUserId, userId)
                .in(AiUsage::getModelId, modelIds);
        List<AiUsage> usages = aiUsageMapper.selectList(wrapper);
        Map<Long, AiUsage> usageByModelId = new HashMap<>();
        for (AiUsage usage : usages) {
            usageByModelId.put(usage.getModelId(), usage);
        }
        return usageByModelId;
    }

    private void clearUserDefaultGrant(Long tenantId, Long userId, Long excludeModelId) {
        LambdaUpdateWrapper<AiModelGrant> clearWrapper = new LambdaUpdateWrapper<>();
        clearWrapper.eq(AiModelGrant::getTenantId, tenantId)
                .eq(AiModelGrant::getUserId, userId)
                .eq(AiModelGrant::getIsDefault, Boolean.TRUE)
                .set(AiModelGrant::getIsDefault, Boolean.FALSE);
        if (excludeModelId != null) {
            clearWrapper.ne(AiModelGrant::getModelId, excludeModelId);
        }
        aiModelGrantMapper.update(null, clearWrapper);
    }

    private Map<Long, AiProvider> loadProviders(List<AiModel> models) {
        if (models == null || models.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> providerIds = new HashSet<>();
        for (AiModel model : models) {
            if (model.getProviderId() != null) {
                providerIds.add(model.getProviderId());
            }
        }
        if (providerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AiProvider> providers = aiProviderMapper.selectByIds(providerIds);
        Map<Long, AiProvider> providerMap = new HashMap<>(providers.size());
        for (AiProvider provider : providers) {
            providerMap.put(provider.getId(), provider);
        }
        return providerMap;
    }

    private Map<Long, AiModel> loadModelsByIds(Long tenantId, Set<Long> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModel::getTenantId, tenantId)
                .in(AiModel::getId, modelIds);
        List<AiModel> models = aiModelMapper.selectList(wrapper);
        Map<Long, AiModel> modelMap = new HashMap<>(models.size());
        for (AiModel model : models) {
            modelMap.put(model.getId(), model);
        }
        return modelMap;
    }

    private Map<String, Permission> getPermissionMapByCode() {
        List<Permission> permissions = permissionMapper.selectSystemPermissions();
        Map<String, Permission> map = new HashMap<>();
        if (permissions == null || permissions.isEmpty()) {
            throw new InternalException("权限字典未配置");
        }
        for (Permission permission : permissions) {
            if (permission == null || !StringUtils.hasText(permission.getCode())) {
                continue;
            }
            map.put(permission.getCode().trim().toUpperCase(Locale.ROOT), permission);
        }
        if (!map.containsKey(PERMISSION_DISABLE) || !map.containsKey(PERMISSION_READ) || !map.containsKey(PERMISSION_ADMIN)) {
            throw new InternalException("权限字典缺失模型授权基础权限");
        }
        return map;
    }

    private Map<Long, Permission> toPermissionMapById(Iterable<Permission> permissions) {
        Map<Long, Permission> map = new HashMap<>();
        if (permissions == null) {
            return map;
        }
        for (Permission permission : permissions) {
            if (permission == null || permission.getId() == null) {
                continue;
            }
            map.put(permission.getId(), permission);
        }
        return map;
    }

    private Permission resolvePermissionByCode(Map<String, Permission> permissionByCode, String permissionCode) {
        String normalized = normalizePermissionCode(permissionCode);
        if (normalized == null) {
            throw new BadRequestException("permission_code 不能为空");
        }
        Permission permission = permissionByCode.get(normalized);
        if (permission == null) {
            throw new BadRequestException("permission_code 无效");
        }
        return permission;
    }

    private boolean isDisablePermission(Map<String, Permission> permissionByCode, String permissionCode) {
        Permission disablePermission = permissionByCode.get(PERMISSION_DISABLE);
        Permission currentPermission = permissionByCode.get(normalizePermissionCode(permissionCode));
        int disableLevel = disablePermission == null || disablePermission.getLevel() == null ? 0 : disablePermission.getLevel();
        int currentLevel = currentPermission == null || currentPermission.getLevel() == null ? 0 : currentPermission.getLevel();
        return currentLevel <= disableLevel;
    }

    private boolean isUsablePermission(Map<String, Permission> permissionByCode, Permission permission) {
        if (permission == null) {
            return false;
        }
        Permission disablePermission = permissionByCode.get(PERMISSION_DISABLE);
        int disableLevel = disablePermission == null || disablePermission.getLevel() == null ? 0 : disablePermission.getLevel();
        int currentLevel = permission.getLevel() == null ? 0 : permission.getLevel();
        return currentLevel > disableLevel;
    }

    private boolean isGrantExpired(AiModelGrant grant) {
        if (grant == null || grant.getExpiresAt() == null) {
            return false;
        }
        return !grant.getExpiresAt().isAfter(LocalDateTime.now());
    }

    private String normalizePermissionCode(String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            return null;
        }
        return permissionCode.trim().toUpperCase(Locale.ROOT);
    }

    private AiProvider getProviderByCode(String providerCode) {
        LambdaQueryWrapper<AiProvider> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiProvider::getCode, providerCode);
        return aiProviderMapper.selectOne(wrapper);
    }

    private boolean existsModelByModelId(Long tenantId, String modelId) {
        LambdaQueryWrapper<AiModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiModel::getTenantId, tenantId)
                .eq(AiModel::getModelId, modelId);
        Long count = aiModelMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private void requireTenantUserExists(Long tenantId, Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("参数错误");
        }
        User user = userMapper.selectByIdAndTenantId(tenantId, userId);
        if (user == null) {
            throw new NotFoundException("用户不存在: %s", userId);
        }
    }

    private Long getRequiredUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new UnauthorizedException("未授权访问");
        }
        return userId;
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new UnauthorizedException("未授权访问");
        }
        return tenantId;
    }

    private LlmManagerDto.ProviderResponse toProviderResponse(AiProvider provider) {
        LlmManagerDto.ProviderResponse response = new LlmManagerDto.ProviderResponse();
        response.setId(provider.getId());
        response.setCode(provider.getCode());
        response.setName(provider.getName());
        response.setBaseUrl(provider.getBaseUrl());
        response.setModelIds(parseJsonList(provider.getModelIds()));
        return response;
    }

    private LlmManagerDto.ModelResponse toModelResponse(AiModel model, AiProvider provider, String tenantCode) {
        LlmManagerDto.ModelResponse response = new LlmManagerDto.ModelResponse();
        response.setId(model.getId());
        response.setModelId(model.getModelId());
        response.setName(model.getName());
        response.setProviderId(model.getProviderId());
        response.setProviderCode(provider == null ? null : provider.getCode());
        response.setProviderName(provider == null ? null : provider.getName());
        response.setModelType(model.getModelType() == null ? null : model.getModelType().getCode());
        response.setDescription(model.getDescription());
        response.setTags(parseJsonList(model.getTags()));
        response.setContextTokens(model.getContextTokens());
        response.setInputPriceUsd(decimalToString(model.getInputPriceUsd()));
        response.setOutputPriceUsd(decimalToString(model.getOutputPriceUsd()));
        response.setEmbeddingDimension(model.getEmbeddingDimension());
        response.setBaseUrl(model.getBaseUrl());
        response.setMaskedApiKey(resolveMaskedApiKey(tenantCode, model.getApiKey()));
        response.setStatus(model.getStatus() == null ? null : model.getStatus().getCode());
        response.setHasApiKey(StringUtils.hasText(model.getApiKey()));
        response.setCreatedBy(model.getCreatedBy());
        response.setUpdatedBy(model.getUpdatedBy());
        response.setCreatedAt(model.getCreatedAt());
        response.setUpdatedAt(model.getUpdatedAt());
        return response;
    }

    private String resolveMaskedApiKey(String tenantCode, String encryptedApiKey) {
        if (!StringUtils.hasText(encryptedApiKey)) {
            return null;
        }
        try {
            String plaintextApiKey = authCryptoClient.decryptLlmApiKey(tenantCode, encryptedApiKey);
            return maskSensitiveValue(plaintextApiKey);
        } catch (RuntimeException ex) {
            return MASK_FALLBACK;
        }
    }

    private String maskSensitiveValue(String value) {
        String normalized = normalizeNullableText(value);
        if (!StringUtils.hasText(normalized)) {
            return MASK_FALLBACK;
        }
        int length = normalized.length();
        if (length <= 2) {
            return "*".repeat(length);
        }
        int prefixLength = Math.min(API_KEY_MASK_VISIBLE_LENGTH, (length - 1) / 2);
        int suffixLength = Math.min(API_KEY_MASK_VISIBLE_LENGTH, length - prefixLength - 1);
        int maskedLength = length - prefixLength - suffixLength;
        return normalized.substring(0, prefixLength)
                + "*".repeat(maskedLength)
                + normalized.substring(length - suffixLength);
    }

    private LlmManagerDto.ModelUsageResponse toModelUsageResponse(AiModelGrant grant,
                                                                  Permission permission,
                                                                  AiUsage usage,
                                                                  AiModel model,
                                                                  AiProvider provider) {
        LlmManagerDto.ModelUsageResponse response = new LlmManagerDto.ModelUsageResponse();
        response.setId(grant.getId());
        response.setUserId(grant.getUserId());
        response.setAiModelId(model.getId());
        response.setModelId(model.getModelId());
        response.setModelName(model.getName());
        response.setModelType(model.getModelType() == null ? null : model.getModelType().getCode());
        response.setModelStatus(model.getStatus() == null ? null : model.getStatus().getCode());
        response.setProviderId(model.getProviderId());
        response.setProviderCode(provider == null ? null : provider.getCode());
        response.setProviderName(provider == null ? null : provider.getName());
        response.setPermissionId(permission == null ? null : permission.getId());
        response.setPermissionCode(permission == null ? null : normalizePermissionCode(permission.getCode()));
        response.setPermissionLevel(permission == null ? null : permission.getLevel());
        response.setIsDefault(Boolean.TRUE.equals(grant.getIsDefault()));
        response.setCallCount(decimalToString(usage == null ? null : toBigDecimal(usage.getCallCount())));
        response.setPromptTokens(decimalToString(usage == null ? null : toBigDecimal(usage.getPromptTokens())));
        response.setCompletionTokens(decimalToString(usage == null ? null : toBigDecimal(usage.getCompletionTokens())));
        response.setTotalTokens(decimalToString(usage == null ? null : toBigDecimal(usage.getTotalTokens())));
        response.setTotalCostUsd(decimalToString(usage == null ? null : usage.getTotalCostUsd()));
        response.setGrantedBy(grant.getGrantedBy());
        response.setGrantedAt(grant.getGrantedAt());
        response.setUpdatedBy(grant.getUpdatedBy());
        response.setExpiresAt(grant.getExpiresAt());
        response.setLastUsedAt(usage == null ? null : usage.getLastUsedAt());
        response.setUpdatedAt(grant.getUpdatedAt());
        return response;
    }

    private List<String> parseJsonList(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        try {
            List<String> parsed = objectMapper.readValue(value, STRING_LIST_TYPE);
            return parsed == null ? Collections.emptyList() : parsed;
        } catch (JsonProcessingException ex) {
            throw new InternalException(ex, "服务器内部错误");
        }
    }

    private String writeJsonList(List<String> values) {
        if (values == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON 序列化失败", ex);
        }
    }

    private String resolveProviderName(String name, String providerCode) {
        if (StringUtils.hasText(name)) {
            return normalizeRequiredText(name, "name 不能为空");
        }
        return toProviderDisplayName(providerCode);
    }

    private String toProviderDisplayName(String providerCode) {
        String[] parts = providerCode.split("[-_]+");
        List<String> labels = new ArrayList<>();
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String normalized = part.trim();
            labels.add(Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1));
        }
        if (labels.isEmpty()) {
            return providerCode;
        }
        return String.join(" ", labels);
    }

    private List<String> normalizeStoredModelIds(List<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> normalizedIds = new LinkedHashSet<>();
        for (String modelId : modelIds) {
            if (!StringUtils.hasText(modelId)) {
                continue;
            }
            normalizedIds.add(modelId.trim());
        }
        return new ArrayList<>(normalizedIds);
    }

    private List<String> normalizeIncomingModelIds(List<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> normalizedIds = new LinkedHashSet<>();
        for (String modelId : modelIds) {
            normalizedIds.add(normalizeRequiredText(modelId, "model_id 不能为空"));
        }
        return new ArrayList<>(normalizedIds);
    }

    private BigDecimal toBigDecimal(Long value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value);
    }

    private String decimalToString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String normalizeProviderCode(String providerCode) {
        String normalized = normalizeRequiredText(providerCode, "provider_code 不能为空");
        if (normalized.length() > 32) {
            throw new IllegalArgumentException("provider_code 长度不能超过 32");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    void verifyModelConnection(String providerCode,
                               String modelId,
                               AiModelType modelType,
                               String apiKey,
                               String baseUrl) {
        String normalizedProvider = normalizeProviderCode(providerCode);
        String normalizedModelId = normalizeRequiredText(modelId, "model_id 不能为空");
        String normalizedApiKey = normalizeRequiredText(apiKey, "api_key 不能为空");
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);

        if ("anthropic".equals(normalizedProvider)) {
            verifyAnthropicChat(normalizedModelId, normalizedApiKey, normalizedBaseUrl);
            return;
        }

        if (!OPENAI_COMPATIBLE_PROVIDERS.contains(normalizedProvider)) {
            throw new IllegalArgumentException("不支持的供应商: " + normalizedProvider);
        }

        if (modelType == AiModelType.EMBEDDINGS) {
            verifyOpenAiEmbeddings(normalizedProvider, normalizedModelId, normalizedApiKey, normalizedBaseUrl);
            return;
        }

        verifyOpenAiChat(normalizedProvider, normalizedModelId, normalizedApiKey, normalizedBaseUrl);
    }

    private void verifyOpenAiChat(String providerCode, String modelId, String apiKey, String baseUrl) {
        OpenAiApi openAiApi = buildOpenAiApi(providerCode, apiKey, baseUrl);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelId)
                .temperature(0D)
                .maxTokens(1)
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
        ChatResponse response = chatModel.call(new Prompt("ping"));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalArgumentException("连接失败，请检查 API Key/Base URL");
        }
    }

    private void verifyOpenAiEmbeddings(String providerCode, String modelId, String apiKey, String baseUrl) {
        OpenAiApi openAiApi = buildOpenAiApi(providerCode, apiKey, baseUrl);
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(modelId)
                .build();
        OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options,
                RetryUtils.DEFAULT_RETRY_TEMPLATE);
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of("ping"), options));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            throw new IllegalArgumentException("连接失败，请检查 API Key/Base URL");
        }
    }

    private OpenAiApi buildOpenAiApi(String providerCode, String apiKey, String baseUrl) {
        normalizeProviderCode(providerCode);
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(OPENAI_COMPLETIONS_PATH)
                .embeddingsPath(OPENAI_EMBEDDINGS_PATH)
                .build();
    }

    private void verifyAnthropicChat(String modelId, String apiKey, String baseUrl) {
        AnthropicApi anthropicApi = AnthropicApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(modelId)
                .temperature(0D)
                .maxTokens(1)
                .build();
        AnthropicChatModel chatModel = AnthropicChatModel.builder()
                .anthropicApi(anthropicApi)
                .defaultOptions(options)
                .build();
        ChatResponse response = chatModel.call(new Prompt("ping"));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalArgumentException("连接失败，请检查 API Key/Base URL");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = normalizeRequiredText(baseUrl, "base_url 不能为空");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
