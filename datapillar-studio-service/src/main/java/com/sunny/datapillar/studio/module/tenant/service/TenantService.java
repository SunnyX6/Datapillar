package com.sunny.datapillar.studio.module.tenant.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.studio.module.tenant.dto.TenantDto;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;

/**
 * 租户服务
 */
public interface TenantService {

    IPage<Tenant> listTenants(Integer status, int limit, int offset);

    Long createTenant(TenantDto.Create dto);

    TenantDto.Response getTenant(Long tenantId);

    void updateTenant(Long tenantId, TenantDto.Update dto);

    void updateStatus(Long tenantId, Integer status);
}
