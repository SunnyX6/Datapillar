package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.tenant.dto.SsoConfigDto;
import com.sunny.datapillar.studio.module.tenant.dto.SsoIdentityDto;
import java.util.List;

/**
 * 租户单点登录管理服务
 * 提供租户单点登录管理业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface TenantSsoAdminService {

    List<SsoConfigDto.Response> listConfigs();

    Long createConfig(SsoConfigDto.Create dto);

    void updateConfig(Long configId, SsoConfigDto.Update dto);

    List<SsoIdentityDto.Item> listIdentities(String provider, Long userId);

    Long bindByCode(SsoIdentityDto.BindByCodeRequest request);

    void unbind(Long identityId);
}
