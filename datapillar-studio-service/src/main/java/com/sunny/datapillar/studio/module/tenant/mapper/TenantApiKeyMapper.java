package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.TenantApiKey;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Mapper for tenant API key persistence operations. */
@Mapper
public interface TenantApiKeyMapper extends BaseMapper<TenantApiKey> {

  List<TenantApiKey> selectByTenantId(@Param("tenantId") Long tenantId);

  TenantApiKey selectByTenantIdAndName(
      @Param("tenantId") Long tenantId, @Param("name") String name);

  Integer countUsableByTenantId(@Param("tenantId") Long tenantId, @Param("now") LocalDateTime now);
}
