package com.sunny.datapillar.studio.module.tenant.service.sso.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.dto.SsoIdentityDto;
import com.sunny.datapillar.studio.module.tenant.entity.TenantSsoConfig;
import com.sunny.datapillar.studio.module.tenant.entity.UserIdentity;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantSsoConfigMapper;
import com.sunny.datapillar.studio.module.tenant.mapper.UserIdentityMapper;
import com.sunny.datapillar.studio.module.tenant.service.sso.provider.DingtalkBindingClient;
import com.sunny.datapillar.studio.module.tenant.service.sso.provider.model.DingtalkUserInfo;
import com.sunny.datapillar.studio.module.tenant.service.sso.SsoIdentityService;
import com.sunny.datapillar.studio.module.user.entity.TenantUser;
import com.sunny.datapillar.studio.module.user.mapper.TenantUserMapper;
import com.sunny.datapillar.studio.rpc.crypto.AuthCryptoGenericClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.AlreadyExistsException;
import com.sunny.datapillar.common.exception.InternalException;

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
    private final AuthCryptoGenericClient authCryptoClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<SsoIdentityDto.Item> list(String provider, Long userId) {
        Long tenantId = getRequiredTenantId();
        List<SsoIdentityDto.Item> items = new ArrayList<>();
        LambdaQueryWrapper<UserIdentity> queryWrapper = buildListWrapper(tenantId, provider, userId);
        queryWrapper.orderByDesc(UserIdentity::getUpdatedAt)
                .orderByDesc(UserIdentity::getId);
        List<UserIdentity> records = userIdentityMapper.selectList(queryWrapper);
        for (UserIdentity record : records) {
            SsoIdentityDto.Item item = new SsoIdentityDto.Item();
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
    public Long bindByCode(SsoIdentityDto.BindByCodeRequest request) {
        Long tenantId = getRequiredTenantId();
        if (request == null || request.getUserId() == null || request.getUserId() <= 0) {
            throw new BadRequestException("参数错误");
        }
        String provider = normalizeProvider(request.getProvider());
        validateProvider(provider);

        TenantUser tenantUser = tenantUserMapper.selectByTenantIdAndUserId(tenantId, request.getUserId());
        if (tenantUser == null || tenantUser.getStatus() == null || tenantUser.getStatus() != 1) {
            throw new ForbiddenException("无权限访问");
        }

        TenantSsoConfig config = loadEnabledConfig(tenantId, provider);
        Map<String, Object> configMap = readConfigMap(config.getConfigJson());
        String clientId = trimToNull(readString(configMap, "clientId"));
        String encodedSecret = trimToNull(readString(configMap, "clientSecret"));
        String clientSecret = decryptClientSecret(tenantId, encodedSecret);
        String redirectUri = trimToNull(readString(configMap, "redirectUri"));
        if (clientId == null || clientSecret == null || redirectUri == null) {
            throw new BadRequestException("参数错误");
        }

        DingtalkUserInfo dingtalkUserInfo = dingtalkBindingClient.fetchUserInfo(clientId, clientSecret, request.getAuthCode());
        String externalUserId = trimToNull(dingtalkUserInfo.getUnionId());
        if (externalUserId == null) {
            throw new InternalException("SSO用户标识缺失");
        }

        ensureUnique(tenantId, request.getUserId(), provider, externalUserId);

        UserIdentity identity = new UserIdentity();
        identity.setTenantId(tenantId);
        identity.setUserId(request.getUserId());
        identity.setProvider(provider);
        identity.setExternalUserId(externalUserId);
        identity.setProfileJson(dingtalkUserInfo.getRawJson());

        try {
            userIdentityMapper.insert(identity);
        } catch (DuplicateKeyException ex) {
            throw new AlreadyExistsException(ex, "资源已存在");
        }
        return identity.getId();
    }

    @Override
    @Transactional
    public void unbind(Long identityId) {
        Long tenantId = getRequiredTenantId();
        if (identityId == null || identityId <= 0) {
            throw new BadRequestException("参数错误");
        }
        UserIdentity identity = userIdentityMapper.selectById(identityId);
        if (identity == null || !tenantId.equals(identity.getTenantId())) {
            throw new NotFoundException("资源不存在");
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
            throw new NotFoundException("SSO配置不存在: provider=%s", provider);
        }
        if (config.getStatus() == null || config.getStatus() != 1) {
            throw new ForbiddenException("SSO配置已禁用: provider=%s", provider);
        }
        return config;
    }

    private void ensureUnique(Long tenantId, Long userId, String provider, String externalUserId) {
        LambdaQueryWrapper<UserIdentity> byExternal = new LambdaQueryWrapper<>();
        byExternal.eq(UserIdentity::getTenantId, tenantId)
                .eq(UserIdentity::getProvider, provider)
                .eq(UserIdentity::getExternalUserId, externalUserId)
                .last("LIMIT 1");
        if (userIdentityMapper.selectOne(byExternal) != null) {
            throw new AlreadyExistsException("资源已存在");
        }

        LambdaQueryWrapper<UserIdentity> byUser = new LambdaQueryWrapper<>();
        byUser.eq(UserIdentity::getTenantId, tenantId)
                .eq(UserIdentity::getUserId, userId)
                .eq(UserIdentity::getProvider, provider)
                .last("LIMIT 1");
        if (userIdentityMapper.selectOne(byUser) != null) {
            throw new AlreadyExistsException("资源已存在");
        }
    }

    private Map<String, Object> readConfigMap(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(configJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new InternalException(ex, "SSO配置无效: %s", DINGTALK);
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
            throw new BadRequestException("参数错误");
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
            throw new UnauthorizedException("未授权访问");
        }
        return tenantId;
    }

    private String decryptClientSecret(Long tenantId, String encoded) {
        try {
            return authCryptoClient.decryptSsoClientSecret(tenantId, encoded);
        } catch (IllegalArgumentException ex) {
            throw new InternalException(ex, "SSO配置无效: %s");
        }
    }
}
