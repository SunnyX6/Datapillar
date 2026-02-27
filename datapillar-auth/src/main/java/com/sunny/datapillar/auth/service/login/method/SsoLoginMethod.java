package com.sunny.datapillar.auth.service.login.method;

import com.sunny.datapillar.auth.entity.Tenant;
import com.sunny.datapillar.auth.entity.User;
import com.sunny.datapillar.auth.entity.UserIdentity;
import com.sunny.datapillar.auth.mapper.TenantMapper;
import com.sunny.datapillar.auth.mapper.UserIdentityMapper;
import com.sunny.datapillar.auth.mapper.UserMapper;
import com.sunny.datapillar.auth.service.login.LoginCommand;
import com.sunny.datapillar.auth.service.login.LoginMethod;
import com.sunny.datapillar.auth.service.login.LoginMethodEnum;
import com.sunny.datapillar.auth.service.login.LoginSubject;
import com.sunny.datapillar.auth.service.login.method.sso.SsoConfigReader;
import com.sunny.datapillar.auth.service.login.method.sso.SsoStateStore;
import com.sunny.datapillar.auth.service.login.method.sso.SsoStateStore.StatePayload;
import com.sunny.datapillar.auth.dto.login.response.SsoQrResponse;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoToken;
import com.sunny.datapillar.auth.service.login.method.sso.model.SsoUserInfo;
import com.sunny.datapillar.auth.service.login.method.sso.provider.SsoProvider;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.UnauthorizedException;
import com.sunny.datapillar.common.exception.ForbiddenException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.common.exception.InternalException;

/**
 * 单点登录登录Method组件
 * 负责单点登录登录Method核心逻辑实现
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Component
public class SsoLoginMethod implements LoginMethod {

    private final SsoStateStore ssoStateStore;
    private final SsoConfigReader ssoConfigReader;
    private final UserIdentityMapper userIdentityMapper;
    private final UserMapper userMapper;
    private final TenantMapper tenantMapper;
    private final Map<String, SsoProvider> providerMap = new HashMap<>();

    public SsoLoginMethod(SsoStateStore ssoStateStore,
                          SsoConfigReader ssoConfigReader,
                          UserIdentityMapper userIdentityMapper,
                          UserMapper userMapper,
                          TenantMapper tenantMapper,
                          List<SsoProvider> providers) {
        this.ssoStateStore = ssoStateStore;
        this.ssoConfigReader = ssoConfigReader;
        this.userIdentityMapper = userIdentityMapper;
        this.userMapper = userMapper;
        this.tenantMapper = tenantMapper;
        if (providers != null) {
            for (SsoProvider provider : providers) {
                providerMap.put(normalize(provider.provider()), provider);
            }
        }
    }

    @Override
    public String method() {
        return LoginMethodEnum.SSO.key();
    }

    public SsoQrResponse buildQr(Long tenantId, String provider) {
        if (tenantId == null) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
        }
        String normalizedProvider = normalize(provider);
        if (normalizedProvider == null) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
        }
        String state = ssoStateStore.createState(tenantId, normalizedProvider);
        SsoProviderConfig config = ssoConfigReader.loadConfig(tenantId, normalizedProvider);
        return getProvider(normalizedProvider).buildQr(config, state);
    }

    @Override
    public LoginSubject authenticate(LoginCommand command) {
        if (command == null) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
        }
        String provider = normalize(command.getProvider());
        String code = trimToNull(command.getCode());
        String state = trimToNull(command.getState());
        if (provider == null || code == null || state == null) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("参数错误");
        }

        StatePayload statePayload = ssoStateStore.consumeOrThrow(state, null, provider);
        Long tenantId = statePayload.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("SSO state 无效");
        }

        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            throw new com.sunny.datapillar.common.exception.UnauthorizedException("租户不存在: %s", String.valueOf(tenantId));
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("租户已被禁用: tenantId=%s", tenantId);
        }

        SsoProviderConfig config = ssoConfigReader.loadConfig(tenantId, provider);
        SsoProvider ssoProvider = getProvider(provider);
        SsoToken token = ssoProvider.exchangeCode(config, code);
        SsoUserInfo userInfo = ssoProvider.fetchUserInfo(config, token);
        String externalUserId = trimToNull(ssoProvider.extractExternalUserId(userInfo));
        if (externalUserId == null) {
            throw new com.sunny.datapillar.common.exception.InternalException("SSO用户标识缺失");
        }

        UserIdentity identity = userIdentityMapper.selectByProviderAndExternalUserId(tenantId, provider, externalUserId);
        if (identity == null) {
            throw new com.sunny.datapillar.common.exception.ForbiddenException("无权限访问");
        }

        User user = userMapper.selectById(identity.getUserId());
        if (user == null) {
            throw new com.sunny.datapillar.common.exception.NotFoundException("用户不存在: %s", identity.getUserId());
        }

        return LoginSubject.builder()
                .user(user)
                .tenant(tenant)
                .loginMethod(method())
                .build();
    }

    private SsoProvider getProvider(String provider) {
        SsoProvider ssoProvider = providerMap.get(normalize(provider));
        if (ssoProvider == null) {
            throw new com.sunny.datapillar.common.exception.BadRequestException("SSO提供方不存在: %s", provider);
        }
        return ssoProvider;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
