package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.tenant.entity.TenantFeatureAudit;
import java.util.List;

/**
 * Tenant functionsAuditManagement services Provide tenant functionsAuditManagement business
 * capabilities and domain services
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface TenantFeatureAuditAdminService {

  List<TenantFeatureAudit> listAudits();
}
