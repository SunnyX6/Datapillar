package com.sunny.datapillar.studio.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.user.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户角色Mapper
 * 负责用户角色数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
