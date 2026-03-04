package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.tenant.entity.TenantFeatureAudit;
import java.util.List;

/**
 * FunctionAuditservice Provide functionalityAuditBusiness capabilities and domain services
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface FeatureAuditService {

  List<TenantFeatureAudit> listAudits();
}
