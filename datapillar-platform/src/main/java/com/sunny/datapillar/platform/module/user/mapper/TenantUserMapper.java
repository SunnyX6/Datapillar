package com.sunny.datapillar.platform.module.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.platform.module.user.entity.TenantUser;

/**
 * 租户成员 Mapper
 */
@Mapper
public interface TenantUserMapper extends BaseMapper<TenantUser> {

    TenantUser selectByTenantIdAndUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    int deleteByTenantIdAndUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    int countByUserId(@Param("userId") Long userId);
}
