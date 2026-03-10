package com.sunny.datapillar.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.TenantApiKey;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Mapper for tenant API key authentication lookup. */
@Mapper
public interface TenantApiKeyMapper extends BaseMapper<TenantApiKey> {

  TenantApiKey selectByHash(@Param("keyHash") String keyHash);

  int updateLastUsed(
      @Param("id") Long id,
      @Param("lastUsedAt") LocalDateTime lastUsedAt,
      @Param("lastUsedIp") String lastUsedIp);
}
