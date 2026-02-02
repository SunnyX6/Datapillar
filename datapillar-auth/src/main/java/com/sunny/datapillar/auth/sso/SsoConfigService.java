package com.sunny.datapillar.auth.sso;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.auth.entity.TenantSsoConfig;
import com.sunny.datapillar.auth.mapper.TenantSsoConfigMapper;
import com.sunny.datapillar.auth.sso.model.SsoProviderConfig;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * SSO 配置服务
 */
@Service
@RequiredArgsConstructor
public class SsoConfigService {

    private final TenantSsoConfigMapper tenantSsoConfigMapper;
    private final ObjectMapper objectMapper;

    public SsoProviderConfig loadConfig(Long tenantId, String provider) {
        TenantSsoConfig config = tenantSsoConfigMapper.selectByTenantIdAndProvider(tenantId, provider);
        if (config == null) {
            throw new BusinessException(ErrorCode.AUTH_SSO_CONFIG_NOT_FOUND, provider);
        }
        if (config.getStatus() == null || config.getStatus() != 1) {
            throw new BusinessException(ErrorCode.AUTH_SSO_CONFIG_DISABLED, provider);
        }
        Map<String, Object> map;
        try {
            map = objectMapper.readValue(config.getConfigJson(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AUTH_SSO_CONFIG_INVALID, provider);
        }
        return new SsoProviderConfig(config.getProvider(), config.getBaseUrl(), map);
    }
}
