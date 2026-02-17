package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.tenant.entity.TenantFeatureAudit;
import java.util.List;

/**
 * 功能Audit服务
 * 提供功能Audit业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface FeatureAuditService {

    List<TenantFeatureAudit> listAudits();
}
