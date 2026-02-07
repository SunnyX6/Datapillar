package com.sunny.datapillar.studio.module.features.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sunny.datapillar.studio.module.features.entity.TenantFeatureAudit;

/**
 * 功能授权审计服务
 */
public interface FeatureAuditService {

    IPage<TenantFeatureAudit> listAudits(int limit, int offset);
}
