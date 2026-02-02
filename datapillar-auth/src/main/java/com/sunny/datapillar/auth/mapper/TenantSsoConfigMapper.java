package com.sunny.datapillar.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.TenantSsoConfig;

/**
 * 租户 SSO 配置 Mapper
 */
@Mapper
public interface TenantSsoConfigMapper extends BaseMapper<TenantSsoConfig> {
    TenantSsoConfig selectByTenantIdAndProvider(@Param("tenantId") Long tenantId,
                                               @Param("provider") String provider);
}
