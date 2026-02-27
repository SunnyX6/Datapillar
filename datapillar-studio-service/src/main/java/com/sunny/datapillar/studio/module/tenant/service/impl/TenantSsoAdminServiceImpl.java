package com.sunny.datapillar.studio.module.tenant.service.impl;

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
    public List<SsoConfigResponse> listConfigs() {
        return ssoConfigService.listConfigs();
    }

    @Override
    public Long createConfig(SsoConfigCreateRequest dto) {
        return ssoConfigService.createConfig(dto);
    }

    @Override
    public void updateConfig(Long configId, SsoConfigUpdateRequest dto) {
        ssoConfigService.updateConfig(configId, dto);
    }

    @Override
    public List<SsoIdentityItem> listIdentities(String provider, Long userId) {
        return ssoIdentityService.list(provider, userId);
    }

    @Override
    public Long bindByCode(SsoIdentityBindByCodeRequest request) {
        return ssoIdentityService.bindByCode(request);
    }

    @Override
    public void unbind(Long identityId) {
        ssoIdentityService.unbind(identityId);
    }
}
