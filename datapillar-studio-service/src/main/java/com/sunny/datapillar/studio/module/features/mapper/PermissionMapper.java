package com.sunny.datapillar.studio.module.features.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.studio.module.features.entity.Permission;

/**
 * 权限 Mapper 接口
 *
 * @author sunny
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
