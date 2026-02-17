package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.UserIdentity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户IdentityMapper
 * 负责用户Identity数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface UserIdentityMapper extends BaseMapper<UserIdentity> {
}
