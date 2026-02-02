package com.sunny.datapillar.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.UserIdentity;

/**
 * 用户身份映射 Mapper
 */
@Mapper
public interface UserIdentityMapper extends BaseMapper<UserIdentity> {
    UserIdentity selectByProviderAndExternalUserId(@Param("tenantId") Long tenantId,
                                                   @Param("provider") String provider,
                                                   @Param("externalUserId") String externalUserId);
}
