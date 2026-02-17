package com.sunny.datapillar.studio.module.tenant.service.sso.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.crypto.SecretCodec;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.dto.SsoConfigDto;
import com.sunny.datapillar.studio.module.tenant.entity.TenantSsoConfig;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantSsoConfigMapper;
import com.sunny.datapillar.studio.module.tenant.service.sso.SsoConfigService;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoGenericClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.InternalException;

/**
 * 单点登录配置服务实现
 * 实现单点登录配置业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class SsoConfigServiceImpl implements SsoConfigService {

    private static final String DINGTALK = "dingtalk";
    private static final int STATUS_ENABLED = 1;
    private static final int STATUS_DISABLED = 0;

    private final TenantSsoConfigMapper tenantSsoConfigMapper;
    private final AuthCryptoGenericClient authCryptoClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<SsoConfigDto.Response> listConfigs() {
        Long tenantId = getRequiredTenantId();
        LambdaQueryWrapper<TenantSsoConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantSsoConfig::getTenantId, tenantId)
                .orderByDesc(TenantSsoConfig::getUpdatedAt)
                .orderByDesc(TenantSsoConfig::getId);
        List<TenantSsoConfig> configs = tenantSsoConfigMapper.selectList(wrapper);
        List<SsoConfigDto.Response> result = new ArrayList<>();
        for (TenantSsoConfig config : configs) {
            result.add(toResponse(config));
        }
        return result;
    }

    @Override
    @Transactional
    public Long createConfig(SsoConfigDto.Create dto) {
        Long tenantId = getRequiredTenantId();
        if (dto == null) {
            throw new BadRequestException("参数错误");
        }
        String provider = normalizeProvider(dto.getProvider());
        validateProvider(provider);
        validateStatus(dto.getStatus());

        LambdaQueryWrapper<TenantSsoConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantSsoConfig::getTenantId, tenantId)
                .eq(TenantSsoConfig::getProvider, provider);
        if (tenantSsoConfigMapper.selectOne(wrapper) != null) {
            throw new AlreadyExistsException("资源已存在", provider);
        }

        Map<String, Object> configMap = mergeConfig(tenantId, new HashMap<>(), dto.getConfig());
        requireDingtalkRequiredFields(configMap);

        TenantSsoConfig config = new TenantSsoConfig();
        config.setTenantId(tenantId);
        config.setProvider(provider);
        config.setBaseUrl(trimToNull(dto.getBaseUrl()));
        config.setConfigJson(writeJson(configMap));
        config.setStatus(dto.getStatus() == null ? STATUS_ENABLED : dto.getStatus());
        tenantSsoConfigMapper.insert(config);
        return config.getId();
    }

    @Override
    @Transactional
    public void updateConfig(Long configId, SsoConfigDto.Update dto) {
        Long tenantId = getRequiredTenantId();
        if (configId == null || configId <= 0) {
            throw new BadRequestException("参数错误");
        }
        TenantSsoConfig config = tenantSsoConfigMapper.selectById(configId);
        if (config == null || config.getTenantId() == null || !config.getTenantId().equals(tenantId)) {
            throw new NotFoundException("资源不存在");
        }
        validateProvider(config.getProvider());
        if (dto == null) {
            return;
        }
        if (dto.getStatus() != null) {
            validateStatus(dto.getStatus());
            config.setStatus(dto.getStatus());
        }
        if (dto.getBaseUrl() != null) {
            config.setBaseUrl(trimToNull(dto.getBaseUrl()));
        }
        if (dto.getConfig() != null) {
            Map<String, Object> existingMap = readJsonAsMap(config.getConfigJson());
            Map<String, Object> merged = mergeConfig(tenantId, existingMap, dto.getConfig());
            requireDingtalkRequiredFields(merged);
            config.setConfigJson(writeJson(merged));
        }
        tenantSsoConfigMapper.updateById(config);
    }

    private SsoConfigDto.Response toResponse(TenantSsoConfig config) {
        Map<String, Object> configMap = readJsonAsMap(config.getConfigJson());
        SsoConfigDto.DingtalkConfig responseConfig = new SsoConfigDto.DingtalkConfig();
        responseConfig.setClientId(readString(configMap, "clientId"));
        responseConfig.setRedirectUri(readString(configMap, "redirectUri"));
        responseConfig.setScope(readString(configMap, "scope"));
        responseConfig.setResponseType(readString(configMap, "responseType"));
        responseConfig.setPrompt(readString(configMap, "prompt"));
        responseConfig.setCorpId(readString(configMap, "corpId"));

        SsoConfigDto.Response response = new SsoConfigDto.Response();
        response.setId(config.getId());
        response.setTenantId(config.getTenantId());
        response.setProvider(config.getProvider());
        response.setBaseUrl(config.getBaseUrl());
        response.setStatus(config.getStatus());
        response.setUpdatedAt(config.getUpdatedAt());
        response.setConfig(responseConfig);
        response.setHasClientSecret(SecretCodec.hasSecret(readString(configMap, "clientSecret")));
        return response;
    }

    private Map<String, Object> mergeConfig(Long tenantId, Map<String, Object> base, SsoConfigDto.DingtalkConfig input) {
        if (input == null) {
            throw new BadRequestException("参数错误");
        }
        Map<String, Object> merged = new HashMap<>(base == null ? Map.of() : base);

        String clientId = trimToNull(input.getClientId());
        if (clientId != null) {
            merged.put("clientId", clientId);
        }

        String redirectUri = trimToNull(input.getRedirectUri());
        if (redirectUri != null) {
            merged.put("redirectUri", redirectUri);
        }

        if (input.getClientSecret() != null) {
            String clientSecret = trimToNull(input.getClientSecret());
            if (clientSecret != null) {
                merged.put("clientSecret", encryptClientSecret(tenantId, clientSecret));
            }
        }

        applyOptional(merged, "scope", input.getScope());
        applyOptional(merged, "responseType", input.getResponseType());
        applyOptional(merged, "prompt", input.getPrompt());
        applyOptional(merged, "corpId", input.getCorpId());
        return merged;
    }

    private void applyOptional(Map<String, Object> map, String key, String value) {
        if (value == null) {
            return;
        }
        String normalized = trimToNull(value);
        if (normalized == null) {
            map.remove(key);
            return;
        }
        map.put(key, normalized);
    }

    private void requireDingtalkRequiredFields(Map<String, Object> configMap) {
        if (trimToNull(readString(configMap, "clientId")) == null
                || trimToNull(readString(configMap, "redirectUri")) == null
                || trimToNull(readString(configMap, "clientSecret")) == null) {
            throw new BadRequestException("参数错误");
        }
    }

    private void validateStatus(Integer status) {
        if (status == null) {
            return;
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new BadRequestException("参数错误");
        }
    }

    private void validateProvider(String provider) {
        if (!DINGTALK.equals(provider)) {
            throw new BadRequestException("参数错误");
        }
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Map<String, Object> readJsonAsMap(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new BadRequestException(ex, "参数错误");
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BadRequestException(ex, "参数错误");
        }
    }

    private String readString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String encryptClientSecret(Long tenantId, String secret) {
        try {
            return authCryptoClient.encryptSsoClientSecret(tenantId, secret);
        } catch (IllegalArgumentException ex) {
            throw new InternalException(ex, "SSO配置无效: %s");
        }
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new UnauthorizedException("未授权访问");
        }
        return tenantId;
    }
}
