package com.sunny.datapillar.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.TenantSsoConfig;

/**
 * 租户单点登录配置Mapper
 * 负责租户单点登录配置数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface TenantSsoConfigMapper extends BaseMapper<TenantSsoConfig> {
    TenantSsoConfig selectByTenantIdAndProvider(@Param("tenantId") Long tenantId,
                                               @Param("provider") String provider);
}
