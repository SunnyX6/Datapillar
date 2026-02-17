package com.sunny.datapillar.studio.module.tenant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunny.datapillar.common.exception.DatapillarRuntimeException;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.tenant.entity.TenantFeatureAudit;
import com.sunny.datapillar.studio.module.tenant.mapper.TenantFeatureAuditMapper;
import com.sunny.datapillar.studio.module.tenant.service.FeatureAuditService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.sunny.datapillar.common.exception.UnauthorizedException;

/**
 * 功能Audit服务实现
 * 实现功能Audit业务流程与规则校验
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
            throw new UnauthorizedException("未授权访问");
        }
        LambdaQueryWrapper<TenantFeatureAudit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantFeatureAudit::getTenantId, tenantId)
                .orderByDesc(TenantFeatureAudit::getCreatedAt)
                .orderByDesc(TenantFeatureAudit::getId);
        return tenantFeatureAuditMapper.selectList(wrapper);
    }
}
