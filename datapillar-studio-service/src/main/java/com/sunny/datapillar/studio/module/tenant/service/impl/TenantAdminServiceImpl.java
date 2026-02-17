package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.studio.module.tenant.dto.TenantDto;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.service.TenantAdminService;
import com.sunny.datapillar.studio.module.tenant.service.TenantService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户管理服务实现
 * 实现租户管理业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class TenantAdminServiceImpl implements TenantAdminService {

    private final TenantService tenantService;

    @Override
    public List<Tenant> listTenants(Integer status) {
        return tenantService.listTenants(status);
    }

    @Override
    public Long createTenant(TenantDto.Create dto) {
        return tenantService.createTenant(dto);
    }

    @Override
    public TenantDto.Response getTenant(Long tenantId) {
        return tenantService.getTenant(tenantId);
    }

    @Override
    public void updateTenant(Long tenantId, TenantDto.Update dto) {
        tenantService.updateTenant(tenantId, dto);
    }

    @Override
    public void updateStatus(Long tenantId, Integer status) {
        tenantService.updateStatus(tenantId, status);
    }
}
