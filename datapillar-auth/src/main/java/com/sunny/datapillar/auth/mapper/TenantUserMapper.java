package com.sunny.datapillar.auth.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.dto.login.response.TenantOptionItem;
import com.sunny.datapillar.auth.entity.TenantUser;

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


    int countByUserId(@Param("userId") Long userId);

    List<TenantOptionItem> selectTenantOptionsByUserId(@Param("userId") Long userId);
}
