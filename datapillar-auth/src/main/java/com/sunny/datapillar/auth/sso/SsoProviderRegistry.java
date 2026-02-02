package com.sunny.datapillar.auth.sso;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;

/**
 * SSO Provider 路由
 */
@Component
public class SsoProviderRegistry {

    private final Map<String, SsoProvider> providerMap = new HashMap<>();

    public SsoProviderRegistry(List<SsoProvider> providers) {
        if (providers != null) {
            for (SsoProvider provider : providers) {
                providerMap.put(normalize(provider.provider()), provider);
            }
        }
    }

    public SsoProvider getProvider(String provider) {
        String key = normalize(provider);
        SsoProvider ssoProvider = providerMap.get(key);
        if (ssoProvider == null) {
            throw new BusinessException(ErrorCode.AUTH_SSO_PROVIDER_NOT_FOUND, provider);
        }
        return ssoProvider;
    }

    private String normalize(String provider) {
        if (provider == null) {
            return "";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }
}
