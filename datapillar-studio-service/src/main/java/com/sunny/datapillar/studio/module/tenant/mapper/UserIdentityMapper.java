package com.sunny.datapillar.studio.module.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.UserIdentity;
import org.apache.ibatis.annotations.Mapper;

/**
 * UserIdentityMapper Responsible userIdentityData access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface UserIdentityMapper extends BaseMapper<UserIdentity> {}
