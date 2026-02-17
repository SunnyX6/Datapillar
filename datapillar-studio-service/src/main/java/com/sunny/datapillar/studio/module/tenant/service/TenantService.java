package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.tenant.dto.TenantDto;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import java.util.List;

/**
 * 租户服务
 * 提供租户业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface TenantService {

    List<Tenant> listTenants(Integer status);

    Long createTenant(TenantDto.Create dto);

    TenantDto.Response getTenant(Long tenantId);

    void updateTenant(Long tenantId, TenantDto.Update dto);

    void updateStatus(Long tenantId, Integer status);
}
