package com.sunny.datapillar.auth.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.auth.entity.UserRole;

/**
 * 用户角色 Mapper
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
