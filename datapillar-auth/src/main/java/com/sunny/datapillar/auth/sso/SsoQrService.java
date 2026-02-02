package com.sunny.datapillar.auth.sso;

import org.springframework.stereotype.Service;

import com.sunny.datapillar.auth.sso.model.SsoProviderConfig;
import com.sunny.datapillar.auth.sso.model.SsoQrResponse;

import lombok.RequiredArgsConstructor;

/**
 * SSO 扫码配置服务
 */
@Service
@RequiredArgsConstructor
public class SsoQrService {

    private final SsoConfigService ssoConfigService;
    private final SsoProviderRegistry ssoProviderRegistry;
    private final SsoStateStore ssoStateStore;

    public SsoQrResponse buildQr(Long tenantId, String provider) {
        String state = ssoStateStore.createState(tenantId, provider);
        SsoProviderConfig config = ssoConfigService.loadConfig(tenantId, provider);
        SsoProvider ssoProvider = ssoProviderRegistry.getProvider(provider);
        return ssoProvider.buildQr(config, state);
    }
}
