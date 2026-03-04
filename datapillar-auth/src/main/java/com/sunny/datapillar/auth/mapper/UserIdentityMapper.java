package com.sunny.datapillar.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.UserIdentity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper for user identity persistence.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface UserIdentityMapper extends BaseMapper<UserIdentity> {
  UserIdentity selectByProviderAndExternalUserId(
      @Param("tenantId") Long tenantId,
      @Param("provider") String provider,
      @Param("externalUserId") String externalUserId);
}
