package com.sunny.datapillar.studio.module.tenant.service;

import com.sunny.datapillar.studio.module.tenant.entity.TenantFeatureAudit;
import java.util.List;

/**
 * 租户功能Audit管理服务
 * 提供租户功能Audit管理业务能力与领域服务
 *
 * @author Sunny
 * @date 2026-01-01
 */
public interface TenantFeatureAuditAdminService {

    List<TenantFeatureAudit> listAudits();
}
