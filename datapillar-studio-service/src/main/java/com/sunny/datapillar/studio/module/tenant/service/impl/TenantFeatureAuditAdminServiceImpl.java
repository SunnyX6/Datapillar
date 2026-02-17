package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.studio.module.tenant.entity.TenantFeatureAudit;
import com.sunny.datapillar.studio.module.tenant.service.FeatureAuditService;
import com.sunny.datapillar.studio.module.tenant.service.TenantFeatureAuditAdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 租户功能Audit管理服务实现
 * 实现租户功能Audit管理业务流程与规则校验
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class TenantFeatureAuditAdminServiceImpl implements TenantFeatureAuditAdminService {

    private final FeatureAuditService featureAuditService;

    @Override
    public List<TenantFeatureAudit> listAudits() {
        return featureAuditService.listAudits();
    }
}
