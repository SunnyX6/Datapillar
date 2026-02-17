package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.studio.module.tenant.dto.SsoConfigDto;
import com.sunny.datapillar.studio.module.tenant.dto.SsoIdentityDto;
import com.sunny.datapillar.studio.module.tenant.service.TenantSsoAdminService;
import com.sunny.datapillar.studio.module.tenant.service.sso.SsoConfigService;
import com.sunny.datapillar.studio.module.tenant.service.sso.SsoIdentityService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户单点登录管理服务实现
 * 实现租户单点登录管理业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class TenantSsoAdminServiceImpl implements TenantSsoAdminService {

    private final SsoConfigService ssoConfigService;
    private final SsoIdentityService ssoIdentityService;

    @Override
    public List<SsoConfigDto.Response> listConfigs() {
        return ssoConfigService.listConfigs();
    }

    @Override
    public Long createConfig(SsoConfigDto.Create dto) {
        return ssoConfigService.createConfig(dto);
    }

    @Override
    public void updateConfig(Long configId, SsoConfigDto.Update dto) {
        ssoConfigService.updateConfig(configId, dto);
    }

    @Override
    public List<SsoIdentityDto.Item> listIdentities(String provider, Long userId) {
        return ssoIdentityService.list(provider, userId);
    }

    @Override
    public Long bindByCode(SsoIdentityDto.BindByCodeRequest request) {
        return ssoIdentityService.bindByCode(request);
    }

    @Override
    public void unbind(Long identityId) {
        ssoIdentityService.unbind(identityId);
    }
}
