package com.sunny.datapillar.admin.module.user.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.datapillar.admin.module.user.entity.Role;
import com.sunny.datapillar.admin.module.user.entity.RolePermission;

/**
 * 角色 Mapper 接口
 *
 * @author sunny
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 根据用户ID查询角色列表
     */
    List<Role> findByUserId(@Param("userId") Long userId);

    /**
     * 根据角色名称查询角色
     */
    Role findByName(@Param("name") String name);

    /**
     * 删除角色权限关联
     */
    void deleteRolePermissions(@Param("roleId") Long roleId);

    /**
     * 根据角色ID删除用户角色关联
     */
    void deleteUserRolesByRoleId(@Param("roleId") Long roleId);

    /**
     * 插入角色权限关联
     */
    void insertRolePermission(RolePermission rolePermission);
}
