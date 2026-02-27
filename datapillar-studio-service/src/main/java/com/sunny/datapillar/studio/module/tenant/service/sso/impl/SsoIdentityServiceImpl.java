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
import com.sunny.datapillar.common.exception.db.DbStorageException;
import com.sunny.datapillar.common.exception.db.SQLExceptionUtils;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.exception.translator.StudioDbExceptionTranslator;
import com.sunny.datapillar.studio.exception.translator.StudioDbScene;
import com.sunny.datapillar.studio.exception.sso.InvalidSsoIdentityRequestException;
import com.sunny.datapillar.studio.exception.sso.SsoConfigDisabledException;
import com.sunny.datapillar.studio.exception.sso.SsoConfigInvalidException;
import com.sunny.datapillar.studio.exception.sso.SsoConfigNotFoundException;
import com.sunny.datapillar.studio.exception.sso.SsoIdentityAccessDeniedException;
import com.sunny.datapillar.studio.module.tenant.entity.TenantSsoConfig;
import com.sunny.datapillar.studio.exception.sso.SsoIdentityNotFoundException;
import com.sunny.datapillar.studio.exception.sso.SsoProviderUnavailableException;
import com.sunny.datapillar.studio.exception.sso.SsoUnauthorizedException;
import com.sunny.datapillar.studio.exception.sso.UnsupportedSsoProviderException;
import com.sunny.datapillar.studio.module.tenant.entity.UserIdentity;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantSsoConfigMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.UserIdentityMapper;
import com.sunny.datapillar.studio.module.tenant.service.TenantCodeResolver;
import com.sunny.datapillar.studio.module.tenant.service.sso.provider.DingtalkBindingClient;
import com.sunny.datapillar.studio.module.tenant.service.sso.provider.model.DingtalkUserInfo;
import com.sunny.datapillar.studio.module.tenant.service.sso.SsoIdentityService;
import com.sunny.datapillar.studio.module.user.entity.TenantUser;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
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
 * 单点登录Identity服务实现
 * 实现单点登录Identity业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class SsoIdentityServiceImpl implements SsoIdentityService {

    private static final String DINGTALK = "dingtalk";

    private final UserIdentityMapper userIdentityMapper;
    private final TenantSsoConfigMapper tenantSsoConfigMapper;
    private final TenantUserMapper tenantUserMapper;
    private final DingtalkBindingClient dingtalkBindingClient;
    private final AuthCryptoRpcClient authCryptoClient;
    private final TenantCodeResolver tenantCodeResolver;
    private final ObjectMapper objectMapper;
    private final StudioDbExceptionTranslator studioDbExceptionTranslator;

    @Override
    public List<SsoIdentityItem> list(String provider, Long userId) {
        Long tenantId = getRequiredTenantId();
        List<SsoIdentityItem> items = new ArrayList<>();
        LambdaQueryWrapper<UserIdentity> queryWrapper = buildListWrapper(tenantId, provider, userId);
        queryWrapper.orderByDesc(UserIdentity::getUpdatedAt)
                .orderByDesc(UserIdentity::getId);
        List<UserIdentity> records = userIdentityMapper.selectList(queryWrapper);
        for (UserIdentity record : records) {
            SsoIdentityItem item = new SsoIdentityItem();
            item.setId(record.getId());
            item.setUserId(record.getUserId());
            item.setProvider(record.getProvider());
            item.setExternalUserId(record.getExternalUserId());
            item.setCreatedAt(record.getCreatedAt());
            item.setUpdatedAt(record.getUpdatedAt());
            items.add(item);
        }
        return items;
    }

    @Override
    @Transactional
    public Long bindByCode(SsoIdentityBindByCodeRequest request) {
        Long tenantId = getRequiredTenantId();
        if (request == null || request.getUserId() == null || request.getUserId() <= 0) {
            throw new InvalidSsoIdentityRequestException();
        }
        String provider = normalizeProvider(request.getProvider());
        validateProvider(provider);

        TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, request.getUserId());
        if (tenantUser == null || tenantUser.getStatus() == null || tenantUser.getStatus() != 1) {
            throw new SsoIdentityAccessDeniedException();
        }

        TenantSsoConfig config = loadEnabledConfig(tenantId, provider);
        Map<String, Object> configMap = readConfigMap(config.getConfigJson());
        String tenantCode = tenantCodeResolver.requireTenantCode(tenantId);
        String clientId = trimToNull(readString(configMap, "clientId"));
        String encodedSecret = trimToNull(readString(configMap, "clientSecret"));
        String clientSecret = decryptClientSecret(tenantCode, encodedSecret);
        String redirectUri = trimToNull(readString(configMap, "redirectUri"));
        if (clientId == null || clientSecret == null || redirectUri == null) {
            throw new SsoConfigInvalidException();
        }

        DingtalkUserInfo dingtalkUserInfo = dingtalkBindingClient.fetchUserInfo(clientId, clientSecret, request.getAuthCode());
        String externalUserId = trimToNull(dingtalkUserInfo.getUnionId());
        if (externalUserId == null) {
            throw new SsoProviderUnavailableException();
        }

        UserIdentity identity = new UserIdentity();
        identity.setTenantId(tenantId);
        identity.setUserId(request.getUserId());
        identity.setProvider(provider);
        identity.setExternalUserId(externalUserId);
        identity.setProfileJson(dingtalkUserInfo.getRawJson());

        try {
            userIdentityMapper.insert(identity);
        } catch (RuntimeException re) {
            throw translateDbException(re, StudioDbScene.STUDIO_SSO_IDENTITY_BIND);
        }
        return identity.getId();
    }

    @Override
    @Transactional
    public void unbind(Long identityId) {
        Long tenantId = getRequiredTenantId();
        if (identityId == null || identityId <= 0) {
            throw new InvalidSsoIdentityRequestException();
        }
        UserIdentity identity = userIdentityMapper.selectById(identityId);
        if (identity == null || !tenantId.equals(identity.getTenantId())) {
            throw new SsoIdentityNotFoundException();
        }
        userIdentityMapper.deleteById(identityId);
    }

    private LambdaQueryWrapper<UserIdentity> buildListWrapper(Long tenantId, String provider, Long userId) {
        LambdaQueryWrapper<UserIdentity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserIdentity::getTenantId, tenantId);

        String normalizedProvider = normalizeProvider(provider);
        if (normalizedProvider != null) {
            validateProvider(normalizedProvider);
            wrapper.eq(UserIdentity::getProvider, normalizedProvider);
        }
        if (userId != null && userId > 0) {
            wrapper.eq(UserIdentity::getUserId, userId);
        }
        return wrapper;
    }

    private TenantSsoConfig loadEnabledConfig(Long tenantId, String provider) {
        LambdaQueryWrapper<TenantSsoConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantSsoConfig::getTenantId, tenantId)
                .eq(TenantSsoConfig::getProvider, provider)
                .last("LIMIT 1");
        TenantSsoConfig config = tenantSsoConfigMapper.selectOne(wrapper);
        if (config == null) {
            throw new SsoConfigNotFoundException();
        }
        if (config.getStatus() == null || config.getStatus() != 1) {
            throw new SsoConfigDisabledException();
        }
        return config;
    }

    private Map<String, Object> readConfigMap(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(configJson, new TypeReference<>() {
            });
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

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private void validateProvider(String provider) {
        if (!DINGTALK.equals(provider)) {
            throw new UnsupportedSsoProviderException();
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Long getRequiredTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new SsoUnauthorizedException();
        }
        return tenantId;
    }

    private String decryptClientSecret(String tenantCode, String encoded) {
        try {
            return authCryptoClient.decryptSsoClientSecret(tenantCode, encoded);
        } catch (IllegalArgumentException ex) {
            throw new SsoConfigInvalidException(ex);
        }
    }

    private RuntimeException translateDbException(RuntimeException runtimeException, StudioDbScene scene) {
        DbStorageException dbException = SQLExceptionUtils.translate(runtimeException);
        if (dbException == null) {
            return runtimeException;
        }
        return studioDbExceptionTranslator.map(scene, dbException);
    }
}
