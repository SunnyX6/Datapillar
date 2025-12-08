package com.sunny.admin.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunny.admin.module.user.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限Mapper接口
 * 
 * @author sunny
 * @since 2024-01-01
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
    
    /**
     * 根据角色ID查询权限列表
     * 
     * @param roleId 角色ID
     * @return 权限列表
     */
    @Select("SELECT p.* FROM permissions p " +
            "INNER JOIN role_permissions rp ON p.id = rp.permission_id " +
            "WHERE rp.role_id = #{roleId}")
    List<Permission> selectByRoleId(@Param("roleId") Long roleId);
    
    /**
     * 根据用户ID查询权限列表
     * 
     * @param userId 用户ID
     * @return 权限列表
     */
    @Select("SELECT DISTINCT p.* FROM permissions p " +
            "INNER JOIN role_permissions rp ON p.id = rp.permission_id " +
            "INNER JOIN user_roles ur ON rp.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<Permission> selectByUserId(@Param("userId") Long userId);
}