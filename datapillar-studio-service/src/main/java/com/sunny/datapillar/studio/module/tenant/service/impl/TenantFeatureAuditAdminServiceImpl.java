package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.sunny.datapillar.studio.module.tenant.entity.TenantFeatureAudit;
import com.sunny.datapillar.studio.module.tenant.service.FeatureAuditService;
import com.sunny.datapillar.studio.module.tenant.service.TenantFeatureAuditAdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Tenant functionsAuditManagement service implementation Implement tenant functionsAuditManage
 * business processes and rule verification
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
