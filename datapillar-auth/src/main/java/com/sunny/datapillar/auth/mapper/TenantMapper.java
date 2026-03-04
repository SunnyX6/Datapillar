package com.sunny.datapillar.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Mapper for tenant persistence operations.
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
  Tenant selectByCode(@Param("code") String code);
}
