package com.sunny.datapillar.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.Tenant;

/**
 * 租户Mapper
 * 负责租户数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
    Tenant selectByCode(@Param("code") String code);
}
