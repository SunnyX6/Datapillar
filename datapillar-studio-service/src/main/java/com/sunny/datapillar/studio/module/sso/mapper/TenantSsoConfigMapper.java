package com.sunny.datapillar.studio.module.sso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.sso.entity.TenantSsoConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户 SSO 配置 Mapper
 */
@Mapper
public interface TenantSsoConfigMapper extends BaseMapper<TenantSsoConfig> {

}
