package com.sunny.datapillar.studio.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.user.entity.TenantUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Tenant userMapper Responsible for tenant user data access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface TenantUserMapper extends BaseMapper<TenantUser> {

  TenantUser selectByTenantIdAndUserId(
      @Param("tenantId") Long tenantId, @Param("userId") Long userId);

  int deleteByTenantIdAndUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

  int countByUserId(@Param("userId") Long userId);
}
