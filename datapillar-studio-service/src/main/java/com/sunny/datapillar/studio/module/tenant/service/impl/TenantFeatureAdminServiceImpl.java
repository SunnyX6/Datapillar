package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.studio.module.tenant.dto.FeatureEntitlementDto;
import com.sunny.datapillar.studio.module.tenant.service.FeatureEntitlementService;
import com.sunny.datapillar.studio.module.tenant.service.TenantFeatureAdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户功能管理服务实现
 * 实现租户功能管理业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class TenantFeatureAdminServiceImpl implements TenantFeatureAdminService {

    private final FeatureEntitlementService featureEntitlementService;

    @Override
    public List<FeatureEntitlementDto.Item> listEntitlements() {
        return featureEntitlementService.listEntitlements();
    }

    @Override
    public void updateEntitlements(List<FeatureEntitlementDto.UpdateItem> items) {
        featureEntitlementService.updateEntitlements(items);
    }
}
