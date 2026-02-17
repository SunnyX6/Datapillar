package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.TenantSsoConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户单点登录配置Mapper
 * 负责租户单点登录配置数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface TenantSsoConfigMapper extends BaseMapper<TenantSsoConfig> {

}
