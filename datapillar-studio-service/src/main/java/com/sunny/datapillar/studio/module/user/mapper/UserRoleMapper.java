package com.sunny.datapillar.studio.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.user.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * user roleMapper Responsible for user role data access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {}
