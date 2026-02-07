package com.sunny.datapillar.auth.sso;

import com.sunny.datapillar.auth.sso.SsoStateStore.StatePayload;
import com.sunny.datapillar.auth.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.sso.model.SsoToken;
import com.sunny.datapillar.auth.sso.model.SsoUserInfo;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SSO 授权码登录入口
 */
@Service
@RequiredArgsConstructor
public class SsoAuthService {

    private final SsoStateStore ssoStateStore;
    private final SsoConfigService ssoConfigService;
    private final SsoProviderRegistry ssoProviderRegistry;

    public SsoUserInfo authenticate(Long tenantId, String provider, String authCode, String state) {
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
        String normalizedProvider = normalize(provider);
        if (normalizedProvider == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT);
        }
        StatePayload payload = ssoStateStore.consumeState(state);
        if (payload.getTenantId() == null || !payload.getTenantId().equals(tenantId)) {
            throw new BusinessException(ErrorCode.SSO_STATE_MISMATCH);
        }
        if (payload.getProvider() == null || !payload.getProvider().equals(normalizedProvider)) {
            throw new BusinessException(ErrorCode.SSO_STATE_MISMATCH);
        }

        SsoProviderConfig config = ssoConfigService.loadConfig(tenantId, normalizedProvider);
        SsoProvider ssoProvider = ssoProviderRegistry.getProvider(normalizedProvider);
        SsoToken token = ssoProvider.exchangeCode(config, authCode);
        SsoUserInfo userInfo = ssoProvider.fetchUserInfo(config, token);
        if (userInfo == null || userInfo.getExternalUserId() == null || userInfo.getExternalUserId().isBlank()) {
            throw new BusinessException(ErrorCode.SSO_USER_ID_MISSING);
        }
        return userInfo;
    }

    private String normalize(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        return provider.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
