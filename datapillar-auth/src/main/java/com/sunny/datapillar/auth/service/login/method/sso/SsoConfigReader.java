package com.sunny.datapillar.auth.service.login.method.sso;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.auth.entity.TenantSsoConfig;
import com.sunny.datapillar.auth.mapper.TenantSsoConfigMapper;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoProviderConfig;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.InternalException;

/**
 * 单点登录配置Reader组件
 * 负责单点登录配置Reader核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class SsoConfigReader {

    private final TenantSsoConfigMapper tenantSsoConfigMapper;
    private final ObjectMapper objectMapper;
    private final SsoSecretCodec ssoSecretCodec;

    public SsoConfigReader(TenantSsoConfigMapper tenantSsoConfigMapper,
                           ObjectMapper objectMapper,
                           SsoSecretCodec ssoSecretCodec) {
        this.tenantSsoConfigMapper = tenantSsoConfigMapper;
        this.objectMapper = objectMapper;
        this.ssoSecretCodec = ssoSecretCodec;
    }

    public SsoProviderConfig loadConfig(Long tenantId, String provider) {
        TenantSsoConfig config = tenantSsoConfigMapper.selectByTenantIdAndProvider(tenantId, provider);
        if (config == null) {
            throw new NotFoundException("SSO配置不存在: provider=%s", provider);
        }
        if (config.getStatus() == null || config.getStatus() != 1) {
            throw new ForbiddenException("SSO配置已禁用: provider=%s", provider);
        }
        Map<String, Object> map;
        try {
            map = objectMapper.readValue(config.getConfigJson(), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new InternalException("SSO配置无效: %s", provider);
        }
        String encodedSecret = map.get("clientSecret") == null ? null : String.valueOf(map.get("clientSecret"));
        String clientSecret = ssoSecretCodec.decryptSecret(tenantId, encodedSecret);
        if (clientSecret == null) {
            throw new InternalException("SSO配置无效: %s", "clientSecret");
        }
        map.put("clientSecret", clientSecret);
        return new SsoProviderConfig(config.getProvider(), config.getBaseUrl(), map);
    }
}
