package com.sunny.datapillar.studio.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.user.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * Role permissionsMapper Responsible for role permission data access and persistence mapping
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {}
