package com.sunny.datapillar.studio.module.tenant.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.tenant.entity.Permission;

/**
 * 权限Mapper
 * 负责权限数据访问与持久化映射
 *
 * @author Sunny
 * @date 2026-01-01
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    /**
     * 根据角色ID查询权限列表
     */
    List<Permission> selectByRoleId(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);

    /**
     * 根据用户ID查询权限列表
     */
    List<Permission> selectByUserId(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    /**
     * 根据权限代码查询权限
     */
    Permission findByCode(@Param("code") String code);

    List<Permission> selectSystemPermissions();

    Permission selectSystemByCode(@Param("code") String code);
}
