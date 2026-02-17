package com.sunny.datapillar.studio.module.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.user.entity.TenantUser;

/**
 * 租户用户Mapper
 * 负责租户用户数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface TenantUserMapper extends BaseMapper<TenantUser> {

    TenantUser selectByTenantIdAndUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    int deleteByTenantIdAndUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    int countByUserId(@Param("userId") Long userId);
}
