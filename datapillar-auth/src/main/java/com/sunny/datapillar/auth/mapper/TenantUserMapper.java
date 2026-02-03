package com.sunny.datapillar.auth.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.TenantUser;

/**
 * 租户成员 Mapper
 */
@Mapper
public interface TenantUserMapper extends BaseMapper<TenantUser> {
    TenantUser selectByTenantIdAndUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    int updateTokenSign(@Param("tenantId") Long tenantId,
                        @Param("userId") Long userId,
                        @Param("tokenSign") String tokenSign,
                        @Param("expireTime") LocalDateTime expireTime);

    int clearTokenSign(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    int countByUserId(@Param("userId") Long userId);

    List<com.sunny.datapillar.auth.dto.AuthDto.TenantOption> selectTenantOptionsByUserId(@Param("userId") Long userId);
}
