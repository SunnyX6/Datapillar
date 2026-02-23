package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.common.exception.BadRequestException;
import com.sunny.datapillar.common.exception.NotFoundException;
import com.sunny.datapillar.studio.context.TenantContext;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.entity.Tenant;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 租户编码解析器
 * 负责 tenantId 到 tenantCode 的统一解析
 *
 * @author Sunny
 * @date 2026-02-23
 */
@Component
@RequiredArgsConstructor
public class TenantCodeResolver {

    private final TenantMapper tenantMapper;

    public String requireTenantCode(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new BadRequestException("参数错误");
        }
        TenantContext context = TenantContextHolder.get();
        if (context != null
                && tenantId.equals(context.getTenantId())
                && StringUtils.hasText(context.getTenantCode())) {
            return context.getTenantCode().trim();
        }
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || !StringUtils.hasText(tenant.getCode())) {
            throw new NotFoundException("租户不存在: %s", tenantId);
        }
        return tenant.getCode().trim();
    }
}
