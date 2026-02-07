package com.sunny.datapillar.studio.module.features.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.features.entity.TenantFeatureAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户功能授权审计 Mapper
 */
@Mapper
public interface TenantFeatureAuditMapper extends BaseMapper<TenantFeatureAudit> {
}
