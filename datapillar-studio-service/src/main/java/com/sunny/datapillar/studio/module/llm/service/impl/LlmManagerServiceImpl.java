package com.sunny.datapillar.studio.module.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.llm.dto.LlmManagerDto;
import com.sunny.datapillar.studio.module.llm.entity.AiModel;
import com.sunny.datapillar.studio.module.llm.entity.AiProvider;
import com.sunny.datapillar.studio.module.llm.entity.AiUsage;
import com.sunny.datapillar.studio.module.llm.enums.AiModelStatus;
import com.sunny.datapillar.studio.module.llm.enums.AiModelType;
import com.sunny.datapillar.studio.module.llm.mapper.AiModelMapper;
import com.sunny.datapillar.studio.module.llm.mapper.AiProviderMapper;
import com.sunny.datapillar.studio.module.llm.mapper.AiUsageMapper;
import com.sunny.datapillar.studio.module.llm.service.LlmManagerService;
import com.sunny.datapillar.studio.module.user.entity.User;
import com.sunny.datapillar.studio.module.user.mapper.UserMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoGenericClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

    private static final int USAGE_STATUS_ENABLED = 1;
    private static final int USAGE_STATUS_DISABLED = 0;
    private static final Set<String> OPENAI_COMPATIBLE_PROVIDERS = Set.of(
            "openai", "deepseek", "openrouter", "ollama", "glm", "mistral", "google", "meta"
    );

    private final AiProviderMapper aiProviderMapper;
    private final AiModelMapper aiModelMapper;
    private final AiUsageMapper aiUsageMapper;
    private final UserMapper userMapper;
    private final AuthCryptoGenericClient authCryptoClient;
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
        List<LlmManagerDto.ModelResponse> rows = new ArrayList<>(models.size());
        for (AiModel model : models) {
            rows.add(toModelResponse(model, providers.get(model.getProviderId())));
        }
        return rows;
    }

    @Override
    public LlmManagerDto.ModelResponse getModel(Long userId, Long modelId) {
        getRequiredUserId(userId);
        Long tenantId = getRequiredTenantId();
        AiModel model = getModelOrThrow(modelId, tenantId);
        AiProvider provider = aiProviderMapper.selectById(model.getProviderId());
        return toModelResponse(model, provider);
    }

    @Override
    @Transactional
    public LlmManagerDto.ModelResponse createModel(Long userId, LlmManagerDto.CreateRequest request) {
        if (request == null) {
            throw new BadRequestException("参数错误");
        }
        Long tenantId = getRequiredTenantId();
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
        model.setApiKey(null);
        model.setBaseUrl(normalizeNullableText(request.getBaseUrl()));
        model.setStatus(AiModelStatus.CONNECT);
        model.setCreatedBy(operatorUserId);
        model.setUpdatedBy(operatorUserId);

        int inserted = aiModelMapper.insert(model);
        if (inserted == 0 || model.getId() == null) {
            throw new InternalException("服务器内部错误");
        }
        return toModelResponse(model, provider);
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
        return toModelResponse(model, provider);
    }

    @Override
    @Transactional
    public void deleteModel(Long userId, Long modelId) {
        getRequiredUserId(userId);
        Long tenantId = getRequiredTenantId();
        AiModel model = getModelOrThrow(modelId, tenantId);

        LambdaQueryWrapper<AiUsage> usageWrapper = new LambdaQueryWrapper<>();
        usageWrapper.eq(AiUsage::getTenantId, tenantId)
                .eq(AiUsage::getModelId, model.getId());
        Long usageCount = aiUsageMapper.selectCount(usageWrapper);
        if (usageCount != null && usageCount > 0) {
            throw new IllegalArgumentException("模型已下发，不能删除");
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

        String encryptedApiKey = authCryptoClient.encryptLlmApiKey(tenantId, apiKey);

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
        return listModelUsagesByUser(tenantId, targetUserId, onlyEnabled);
    }

    @Override
    @Transactional
    public LlmManagerDto.ModelUsageResponse grantUserModelUsage(Long operatorUserId, Long targetUserId, Long modelId) {
        Long tenantId = getRequiredTenantId();
        Long operatorId = getRequiredUserId(operatorUserId);
        requireTenantUserExists(tenantId, targetUserId);

        AiModel model = getModelOrThrow(modelId, tenantId);
        if (model.getStatus() != AiModelStatus.ACTIVE) {
            throw new IllegalArgumentException("仅已连接模型可以下发");
        }

        LambdaQueryWrapper<AiUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiUsage::getTenantId, tenantId)
                .eq(AiUsage::getUserId, targetUserId)
                .eq(AiUsage::getModelId, modelId);
        AiUsage usage = aiUsageMapper.selectOne(wrapper);
        LocalDateTime now = LocalDateTime.now();

        if (usage == null) {
            usage = new AiUsage();
            usage.setTenantId(tenantId);
            usage.setUserId(targetUserId);
            usage.setModelId(modelId);
            usage.setStatus(USAGE_STATUS_ENABLED);
            usage.setIsDefault(Boolean.FALSE);
            usage.setTotalCostUsd(BigDecimal.ZERO);
            usage.setGrantedBy(operatorId);
            usage.setGrantedAt(now);
            int inserted = aiUsageMapper.insert(usage);
            if (inserted == 0) {
                throw new InternalException("服务器内部错误");
            }
        } else {
            usage.setStatus(USAGE_STATUS_ENABLED);
            usage.setGrantedBy(operatorId);
            usage.setGrantedAt(now);
            int updated = aiUsageMapper.updateById(usage);
            if (updated == 0) {
                throw new InternalException("服务器内部错误");
            }
        }

        AiProvider provider = aiProviderMapper.selectById(model.getProviderId());
        return toModelUsageResponse(usage, model, provider);
    }

    @Override
    @Transactional
    public void revokeUserModelUsage(Long operatorUserId, Long targetUserId, Long modelId) {
        Long tenantId = getRequiredTenantId();
        getRequiredUserId(operatorUserId);
        requireTenantUserExists(tenantId, targetUserId);

        AiUsage usage = getUsageOrThrow(tenantId, targetUserId, modelId);
        usage.setStatus(USAGE_STATUS_DISABLED);
        usage.setIsDefault(Boolean.FALSE);
        int updated = aiUsageMapper.updateById(usage);
        if (updated == 0) {
            throw new InternalException("服务器内部错误");
        }
    }

    @Override
    @Transactional
    public LlmManagerDto.ModelUsageResponse setUserDefaultModel(Long operatorUserId,
                                                                Long targetUserId,
                                                                Long modelId) {
        Long tenantId = getRequiredTenantId();
        getRequiredUserId(operatorUserId);
        requireTenantUserExists(tenantId, targetUserId);

        AiUsage usage = getUsageOrThrow(tenantId, targetUserId, modelId);
        if (usage.getStatus() == null || usage.getStatus() != USAGE_STATUS_ENABLED) {
            throw new ForbiddenException("无权限访问");
        }

        AiModel model = getModelOrThrow(modelId, tenantId);
        if (model.getStatus() != AiModelStatus.ACTIVE) {
            throw new IllegalArgumentException("仅已连接模型可以设为默认");
        }

        LambdaUpdateWrapper<AiUsage> clearWrapper = new LambdaUpdateWrapper<>();
        clearWrapper.eq(AiUsage::getTenantId, tenantId)
                .eq(AiUsage::getUserId, targetUserId)
                .set(AiUsage::getIsDefault, Boolean.FALSE);
        aiUsageMapper.update(null, clearWrapper);

        LambdaUpdateWrapper<AiUsage> setWrapper = new LambdaUpdateWrapper<>();
        setWrapper.eq(AiUsage::getTenantId, tenantId)
                .eq(AiUsage::getUserId, targetUserId)
                .eq(AiUsage::getModelId, modelId)
                .eq(AiUsage::getStatus, USAGE_STATUS_ENABLED)
                .set(AiUsage::getIsDefault, Boolean.TRUE);
        int updated = aiUsageMapper.update(null, setWrapper);
        if (updated == 0) {
            throw new NotFoundException("资源不存在");
        }

        AiUsage refreshed = getUsageOrThrow(tenantId, targetUserId, modelId);
        AiProvider provider = aiProviderMapper.selectById(model.getProviderId());
        return toModelUsageResponse(refreshed, model, provider);
    }

    private List<LlmManagerDto.ModelUsageResponse> listModelUsagesByUser(Long tenantId,
                                                                          Long userId,
                                                                          boolean onlyEnabled) {
        LambdaQueryWrapper<AiUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiUsage::getTenantId, tenantId)
                .eq(AiUsage::getUserId, userId);
        if (onlyEnabled) {
            wrapper.eq(AiUsage::getStatus, USAGE_STATUS_ENABLED);
        }
        wrapper.orderByDesc(AiUsage::getUpdatedAt)
                .orderByDesc(AiUsage::getId);

        List<AiUsage> usages = aiUsageMapper.selectList(wrapper);
        if (usages == null || usages.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> modelIds = new HashSet<>();
        for (AiUsage usage : usages) {
            if (usage.getModelId() != null) {
                modelIds.add(usage.getModelId());
            }
        }

        Map<Long, AiModel> modelMap = loadModelsByIds(tenantId, modelIds);
        Map<Long, AiProvider> providerMap = loadProviders(new ArrayList<>(modelMap.values()));

        List<LlmManagerDto.ModelUsageResponse> rows = new ArrayList<>(usages.size());
        for (AiUsage usage : usages) {
            AiModel model = modelMap.get(usage.getModelId());
            if (model == null) {
                continue;
            }
            AiProvider provider = providerMap.get(model.getProviderId());
            rows.add(toModelUsageResponse(usage, model, provider));
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

    private AiUsage getUsageOrThrow(Long tenantId, Long userId, Long modelId) {
        LambdaQueryWrapper<AiUsage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiUsage::getTenantId, tenantId)
                .eq(AiUsage::getUserId, userId)
                .eq(AiUsage::getModelId, modelId);
        AiUsage usage = aiUsageMapper.selectOne(wrapper);
        if (usage == null) {
            throw new NotFoundException("资源不存在");
        }
        return usage;
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

    private LlmManagerDto.ModelResponse toModelResponse(AiModel model, AiProvider provider) {
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
        response.setStatus(model.getStatus() == null ? null : model.getStatus().getCode());
        response.setHasApiKey(StringUtils.hasText(model.getApiKey()));
        response.setCreatedBy(model.getCreatedBy());
        response.setUpdatedBy(model.getUpdatedBy());
        response.setCreatedAt(model.getCreatedAt());
        response.setUpdatedAt(model.getUpdatedAt());
        return response;
    }

    private LlmManagerDto.ModelUsageResponse toModelUsageResponse(AiUsage usage, AiModel model, AiProvider provider) {
        LlmManagerDto.ModelUsageResponse response = new LlmManagerDto.ModelUsageResponse();
        response.setId(usage.getId());
        response.setUserId(usage.getUserId());
        response.setAiModelId(model.getId());
        response.setModelId(model.getModelId());
        response.setModelName(model.getName());
        response.setModelType(model.getModelType() == null ? null : model.getModelType().getCode());
        response.setModelStatus(model.getStatus() == null ? null : model.getStatus().getCode());
        response.setProviderId(model.getProviderId());
        response.setProviderCode(provider == null ? null : provider.getCode());
        response.setProviderName(provider == null ? null : provider.getName());
        response.setStatus(usage.getStatus());
        response.setIsDefault(Boolean.TRUE.equals(usage.getIsDefault()));
        response.setTotalCostUsd(decimalToString(usage.getTotalCostUsd()));
        response.setGrantedBy(usage.getGrantedBy());
        response.setGrantedAt(usage.getGrantedAt());
        response.setLastUsedAt(usage.getLastUsedAt());
        response.setUpdatedAt(usage.getUpdatedAt());
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
            throw new IllegalArgumentException("tags 格式不合法", ex);
        }
    }

    private String decimalToString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private String normalizeProviderCode(String providerCode) {
        String normalized = normalizeRequiredText(providerCode, "provider_code 不能为空");
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
            verifyOpenAiEmbeddings(normalizedModelId, normalizedApiKey, normalizedBaseUrl);
            return;
        }

        verifyOpenAiChat(normalizedModelId, normalizedApiKey, normalizedBaseUrl);
    }

    private void verifyOpenAiChat(String modelId, String apiKey, String baseUrl) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
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

    private void verifyOpenAiEmbeddings(String modelId, String apiKey, String baseUrl) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();
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
