package com.sunny.datapillar.studio.module.features.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunny.datapillar.common.error.ErrorCode;
import com.sunny.datapillar.common.exception.BusinessException;
import com.sunny.datapillar.studio.context.TenantContextHolder;
import com.sunny.datapillar.studio.module.features.entity.TenantFeatureAudit;
import com.sunny.datapillar.studio.module.features.mapper.TenantFeatureAuditMapper;
import com.sunny.datapillar.studio.module.features.service.FeatureAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 功能授权审计服务实现
 */
@Service
@RequiredArgsConstructor
public class FeatureAuditServiceImpl implements FeatureAuditService {

    private final TenantFeatureAuditMapper tenantFeatureAuditMapper;

    @Override
    public IPage<TenantFeatureAudit> listAudits(int limit, int offset) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        long current = resolveCurrent(limit, offset);
        Page<TenantFeatureAudit> page = Page.of(current, limit);
        LambdaQueryWrapper<TenantFeatureAudit> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TenantFeatureAudit::getTenantId, tenantId)
                .orderByDesc(TenantFeatureAudit::getCreatedAt)
                .orderByDesc(TenantFeatureAudit::getId);
        return tenantFeatureAuditMapper.selectPage(page, wrapper);
    }

    private long resolveCurrent(int limit, int offset) {
        if (limit <= 0) {
            return 1;
        }
        return offset / limit + 1;
    }
}
