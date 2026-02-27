package com.sunny.datapillar.studio.module.tenant.service.sso.impl;

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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.crypto.SecretCodec;
import com.sunny.datapillar.common.exception.db.DbStorageException;
import com.sunny.datapillar.common.exception.db.SQLExceptionUtils;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.exception.translator.StudioDbScene;
import com.sunny.datapillar.studio.module.tenant.entity.TenantSsoConfig;
import com.sunny.datapillar.studio.exception.sso.InvalidSsoConfigRequestException;
import com.sunny.datapillar.studio.exception.sso.SsoConfigInvalidException;
import com.sunny.datapillar.studio.exception.sso.SsoConfigNotFoundException;
import com.sunny.datapillar.studio.exception.sso.SsoUnauthorizedException;
import com.sunny.datapillar.studio.exception.sso.UnsupportedSsoProviderException;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantSsoConfigMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantCodeResolver;
import com.sunny.datapillar.studio.module.tenant.service.sso.SsoConfigService;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoRpcClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AuthCryptoRpcClient authCryptoClient;
    private final TenantCodeResolver tenantCodeResolver;
    private final ObjectMapper objectMapper;
    private final StudioDbExceptionTranslator studioDbExceptionTranslator;

    @Override
    public List<SsoConfigResponse> listConfigs() {
        Long tenantId = getRequiredTenantId();
        LambdaQueryWrapper<TenantSsoConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantSsoConfig::getTenantId, tenantId)
                .orderByDesc(TenantSsoConfig::getUpdatedAt)
                .orderByDesc(TenantSsoConfig::getId);
        List<TenantSsoConfig> configs = tenantSsoConfigMapper.selectList(wrapper);
        List<SsoConfigResponse> result = new ArrayList<>();
        for (TenantSsoConfig config : configs) {
            result.add(toResponse(config));
        }
        return result;
    }

    @Override
    @Transactional
    public Long createConfig(SsoConfigCreateRequest dto) {
        Long tenantId = getRequiredTenantId();
        if (dto == null) {
            throw new InvalidSsoConfigRequestException();
        }
        String provider = normalizeProvider(dto.getProvider());
        validateProvider(provider);
        validateStatus(dto.getStatus());

        String tenantCode = tenantCodeResolver.requireTenantCode(tenantId);
        Map<String, Object> configMap = mergeConfig(tenantCode, new HashMap<>(), dto.getConfig());
        requireDingtalkRequiredFields(configMap);

        TenantSsoConfig config = new TenantSsoConfig();
        config.setTenantId(tenantId);
        config.setProvider(provider);
        config.setBaseUrl(trimToNull(dto.getBaseUrl()));
        config.setConfigJson(writeJson(configMap));
        config.setStatus(dto.getStatus() == null ? STATUS_ENABLED : dto.getStatus());
        try {
            tenantSsoConfigMapper.insert(config);
        } catch (RuntimeException re) {
            throw translateDbException(re, StudioDbScene.STUDIO_SSO_CONFIG);
        }
        return config.getId();
    }

    @Override
    @Transactional
    public void updateConfig(Long configId, SsoConfigUpdateRequest dto) {
        Long tenantId = getRequiredTenantId();
        if (configId == null || configId <= 0) {
            throw new InvalidSsoConfigRequestException();
        }
        TenantSsoConfig config = tenantSsoConfigMapper.selectById(configId);
        if (config == null || config.getTenantId() == null || !config.getTenantId().equals(tenantId)) {
            throw new SsoConfigNotFoundException();
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
            String tenantCode = tenantCodeResolver.requireTenantCode(tenantId);
            Map<String, Object> existingMap = readJsonAsMap(config.getConfigJson());
            Map<String, Object> merged = mergeConfig(tenantCode, existingMap, dto.getConfig());
            requireDingtalkRequiredFields(merged);
            config.setConfigJson(writeJson(merged));
        }
        tenantSsoConfigMapper.updateById(config);
    }

    private SsoConfigResponse toResponse(TenantSsoConfig config) {
        Map<String, Object> configMap = readJsonAsMap(config.getConfigJson());
        SsoDingtalkConfigItem responseConfig = new SsoDingtalkConfigItem();
        responseConfig.setClientId(readString(configMap, "clientId"));
        responseConfig.setRedirectUri(readString(configMap, "redirectUri"));
        responseConfig.setScope(readString(configMap, "scope"));
        responseConfig.setResponseType(readString(configMap, "responseType"));
        responseConfig.setPrompt(readString(configMap, "prompt"));
        responseConfig.setCorpId(readString(configMap, "corpId"));

        SsoConfigResponse response = new SsoConfigResponse();
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

    private Map<String, Object> mergeConfig(String tenantCode, Map<String, Object> base, SsoDingtalkConfigItem input) {
        if (input == null) {
            throw new InvalidSsoConfigRequestException();
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
                merged.put("clientSecret", encryptClientSecret(tenantCode, clientSecret));
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
            throw new InvalidSsoConfigRequestException();
        }
    }

    private void validateStatus(Integer status) {
        if (status == null) {
            return;
        }
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new InvalidSsoConfigRequestException();
        }
    }

    private void validateProvider(String provider) {
        if (!DINGTALK.equals(provider)) {
            throw new UnsupportedSsoProviderException();
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
            throw new SsoConfigInvalidException(ex);
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new SsoConfigInvalidException(ex);
        }
    }

    private String readString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String encryptClientSecret(String tenantCode, String secret) {
        try {
            return authCryptoClient.encryptSsoClientSecret(tenantCode, secret);
        } catch (IllegalArgumentException ex) {
            throw new SsoConfigInvalidException(ex);
        }
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new SsoUnauthorizedException();
        }
        return tenantId;
    }

    private RuntimeException translateDbException(RuntimeException runtimeException, StudioDbScene scene) {
        DbStorageException dbException = SQLExceptionUtils.translate(runtimeException);
        if (dbException == null) {
            return runtimeException;
        }
        return studioDbExceptionTranslator.map(scene, dbException);
    }
}
