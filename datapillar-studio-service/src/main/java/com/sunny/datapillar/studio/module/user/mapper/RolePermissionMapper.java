package com.sunny.datapillar.studio.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.user.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色权限Mapper
 * 负责角色权限数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
