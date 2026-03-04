package com.sunny.datapillar.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.TenantSsoConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper for tenant SSO configuration persistence.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface TenantSsoConfigMapper extends BaseMapper<TenantSsoConfig> {
  TenantSsoConfig selectByTenantIdAndProvider(
      @Param("tenantId") Long tenantId, @Param("provider") String provider);
}
