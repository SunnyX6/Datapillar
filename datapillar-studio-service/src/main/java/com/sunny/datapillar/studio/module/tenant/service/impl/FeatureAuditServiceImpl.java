package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.entity.TenantFeatureAudit;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantFeatureAuditMapper;
import com.sunny.datapillar.studio.module.tenant.service.FeatureAuditService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * FunctionAuditService implementation Implement functionAuditBusiness process and rule verification
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class FeatureAuditServiceImpl implements FeatureAuditService {

  private final TenantFeatureAuditMapper tenantFeatureAuditMapper;

  @Override
  public List<TenantFeatureAudit> listAudits() {
    Long tenantId = TenantContextHolder.getTenantId();
    if (tenantId == null) {
      throw new com.sunny.datapillar.common.exception.UnauthorizedException("Unauthorized access");
    }
    LambdaQueryWrapper<TenantFeatureAudit> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .eq(TenantFeatureAudit::getTenantId, tenantId)
        .orderByDesc(TenantFeatureAudit::getCreatedAt)
        .orderByDesc(TenantFeatureAudit::getId);
    return tenantFeatureAuditMapper.selectList(wrapper);
  }
}
