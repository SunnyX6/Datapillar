package com.sunny.datapillar.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.dto.login.response.TenantOptionItem;
import com.sunny.datapillar.auth.entity.TenantUser;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper for tenant-user relation persistence.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface TenantUserMapper extends BaseMapper<TenantUser> {
  TenantUser selectByTenantIdAndUserId(
      @Param("tenantId") Long tenantId, @Param("userId") Long userId);

  int countByUserId(@Param("userId") Long userId);

  List<TenantOptionItem> selectTenantOptionsByUserId(@Param("userId") Long userId);
}
