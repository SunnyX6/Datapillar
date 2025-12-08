package com.sunny.admin.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.admin.module.user.entity.Role;
import com.sunny.admin.module.user.entity.RolePermission;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 角色Mapper接口
 * 
 * @author sunny
 * @since 2024-01-01
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
    
    /**
     * 根据用户ID查询角色列表
     * 
     * @param userId 用户ID
     * @return 角色列表
     */
    @Select("SELECT r.* FROM roles r " +
            "INNER JOIN user_roles ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<Role> findByUserId(@Param("userId") Long userId);
    
    /**
     * 根据角色代码查询角色
     * 
     * @param code 角色代码
     * @return 角色信息
     */
    @Select("SELECT * FROM roles WHERE code = #{code}")
    Role findByCode(@Param("code") String code);
    
    /**
     * 删除角色权限关联
     * 
     * @param roleId 角色ID
     */
    @Delete("DELETE FROM role_permissions WHERE role_id = #{roleId}")
    void deleteRolePermissions(@Param("roleId") Long roleId);
    
    /**
     * 根据角色ID删除用户角色关联
     * 
     * @param roleId 角色ID
     */
    @Delete("DELETE FROM user_roles WHERE role_id = #{roleId}")
    void deleteUserRolesByRoleId(@Param("roleId") Long roleId);
    
    /**
     * 插入角色权限关联
     * 
     * @param rolePermission 角色权限关联
     */
    @Insert("INSERT INTO role_permissions (role_id, permission_id, created_at) VALUES (#{roleId}, #{permissionId}, #{createdAt})")
    void insertRolePermission(RolePermission rolePermission);
}