package com.sunny.datapillar.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.UserIdentity;

/**
 * 用户IdentityMapper
 * 负责用户Identity数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface UserIdentityMapper extends BaseMapper<UserIdentity> {
    UserIdentity selectByProviderAndExternalUserId(@Param("tenantId") Long tenantId,
                                                   @Param("provider") String provider,
                                                   @Param("externalUserId") String externalUserId);
}
