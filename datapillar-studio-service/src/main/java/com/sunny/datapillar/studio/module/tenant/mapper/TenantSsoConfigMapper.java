package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.TenantSsoConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * Tenant single sign-on configurationMapper Responsible for tenant single sign-on configuration
 * data access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface TenantSsoConfigMapper extends BaseMapper<TenantSsoConfig> {}
